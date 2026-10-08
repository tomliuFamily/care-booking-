import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";

import {
  api,
  downloadBookingPdf,
  restoreSession,
  signIn,
  signOut,
  signUp,
} from "./api";

import CareWorkPanel, {
  workRequest,
} from "./CareWorkPanel";

const ROLE_NAMES = {
  ADMIN: "管理者",
  CUSTOMER: "顧客",
  CAREGIVER: "照服員",
};

const STATUS_NAMES = {
  PENDING: "待確認",
  CONFIRMED: "已確認",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
};

function pad(number) {
  return String(number).padStart(2, "0");
}

function taipeiToday() {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Taipei",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());

  const value = (type) =>
    parts.find((part) => part.type === type).value;

  return `${value("year")}-${value("month")}-${value("day")}`;
}

function monthText(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
}

function dateText(year, monthIndex, day) {
  return `${year}-${pad(monthIndex + 1)}-${pad(day)}`;
}

function timeText(value) {
  return value.slice(11, 16);
}

function timeReached(value) {
  return Date.now() >= new Date(`${value}+08:00`).getTime();
}

function AuthPanel({ onLogin }) {
  const [register, setRegister] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("customer1@care.test");
  const [password, setPassword] = useState("Demo12345!");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();

    if (busy) return;

    setBusy(true);
    setError("");

    try {
      const user = register
        ? await signUp(name, email, password)
        : await signIn(email, password);

      onLogin(user);
    } catch (error) {
      setError(error.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-intro">
        <span className="eyebrow">CARE WITH WARMTH</span>

        <h1>
          把照顧安排好，
          <br />
          讓家人更安心。
        </h1>

        <p>
          選擇合適的服務與時段，
          讓每一次照護都有清楚的安排。
        </p>

        <div className="intro-tags">
          <span>月曆預約</span>
          <span>照服安排</span>
          <span>預約單下載</span>
        </div>
      </section>

      <section className="panel login-card">
        <h2>
          {register ? "顧客註冊" : "安心長照登入"}
        </h2>

        <form onSubmit={submit} className="form-stack">
          {register && (
            <label>
              姓名
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={50}
                disabled={busy}
                required
              />
            </label>
          )}

          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              maxLength={150}
              autoComplete="username"
              disabled={busy}
              required
            />
          </label>

          <label>
            密碼
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={register ? 8 : undefined}
              maxLength={60}
              autoComplete={
                register ? "new-password" : "current-password"
              }
              disabled={busy}
              required
            />
          </label>

          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}

          <button disabled={busy}>
            {busy
              ? "處理中…"
              : register
                ? "註冊並登入"
                : "登入"}
          </button>
        </form>

        <button
          type="button"
          className="link-button"
          disabled={busy}
          onClick={() => {
            setRegister(!register);
            setError("");
          }}
        >
          {register
            ? "已有帳號，返回登入"
            : "沒有帳號？註冊顧客帳號"}
        </button>

        <div className="demo-accounts">
          <strong>示範帳號</strong>
          <p>顧客：customer1@care.test</p>
          <p>照服員：caregiver1@care.test</p>
          <p>管理者：admin@care.test</p>
          <p>密碼：Demo12345!</p>
        </div>
      </section>
    </main>
  );
}

function Dashboard({ user, onLogout }) {
  const today = taipeiToday();

  const [month, setMonth] = useState(
    () => new Date(`${today.slice(0, 7)}-01T12:00:00`)
  );

  const [selectedDate, setSelectedDate] = useState(today);

  const [services, setServices] = useState([]);
  const [caregivers, setCaregivers] = useState([]);
  const [slots, setSlots] = useState([]);
  const [bookings, setBookings] = useState([]);

  const [careCases, setCareCases] = useState([]);
  const [flows, setFlows] = useState([]);
  const [customers, setCustomers] = useState([]);

  const [serviceId, setServiceId] = useState("");
  const [slotId, setSlotId] = useState("");
  const [caseId, setCaseId] = useState("");
  const [address, setAddress] = useState("");
  const [note, setNote] = useState("");

  const [caregiverId, setCaregiverId] = useState(
    user.role === "CAREGIVER" ? String(user.id) : ""
  );

  const [startHour, setStartHour] = useState("09");

  const [tab, setTab] = useState("calendar");
  const [selectedBookingId, setSelectedBookingId] = useState(null);
  const [revision, setRevision] = useState(0);

  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const loadVersion = useRef(0);
  const actionLock = useRef(false);

  const monthValue = monthText(month);
  const disabled = busy || loading;

  const reload = useCallback(async () => {
    const version = ++loadVersion.current;

    setLoading(true);

    try {
      const [
        serviceData,
        caregiverData,
        slotData,
        bookingData,
        caseData,
        flowData,
        customerData,
      ] = await Promise.all([
        api("/services"),
        api("/caregivers"),
        api(`/slots?month=${monthValue}`),
        api(`/bookings?month=${monthValue}`),
        api("/work/cases"),
        api(`/work/flows?month=${monthValue}`),
        user.role === "ADMIN"
          ? api("/work/customers")
          : Promise.resolve([]),
      ]);

      if (version !== loadVersion.current) {
        return false;
      }

      setServices(serviceData);
      setCaregivers(caregiverData);
      setSlots(slotData);
      setBookings(bookingData);
      setCareCases(caseData);
      setFlows(flowData);
      setCustomers(customerData);

      return true;
    } catch (error) {
      if (version === loadVersion.current) {
        setError(error.message);
      }

      return false;
    } finally {
      if (version === loadVersion.current) {
        setLoading(false);
      }
    }
  }, [monthValue, user.role]);

  useEffect(() => {
    setSlots([]);
    setBookings([]);
    setFlows([]);
    setSelectedBookingId(null);
    setError("");
    setMessage("");

    void reload();

    return () => {
      loadVersion.current += 1;
    };
  }, [reload]);

  useEffect(() => {
    if (!serviceId && services.length > 0) {
      setServiceId(String(services[0].id));
    }
  }, [serviceId, services]);

  useEffect(() => {
    if (
      user.role === "ADMIN" &&
      !caregiverId &&
      caregivers.length > 0
    ) {
      setCaregiverId(String(caregivers[0].id));
    }
  }, [user.role, caregiverId, caregivers]);

  useEffect(() => {
    if (user.role !== "CUSTOMER") return;

    const active = careCases.filter((item) => item.active);

    const current = active.find(
      (item) => String(item.id) === caseId
    );

    if (!current) {
      const first = active[0];

      setCaseId(first ? String(first.id) : "");

      if (first) {
        setAddress((previous) => previous || first.address);
      }
    }
  }, [careCases, caseId, user.role]);

  useEffect(() => {
    setSlotId("");
  }, [selectedDate]);

  const daySlots = slots.filter(
    (slot) => slot.startAt.slice(0, 10) === selectedDate
  );

  const dayBookings = bookings.filter(
    (booking) => booking.startAt.slice(0, 10) === selectedDate
  );

  const activeCases = careCases.filter((item) => item.active);

  const selectedService = services.find(
    (service) => String(service.id) === serviceId
  );

  async function perform(action, successMessage = "操作已完成") {
    if (actionLock.current || loading) {
      return false;
    }

    actionLock.current = true;
    setBusy(true);
    setError("");
    setMessage("");

    try {
      await action();

      setRevision((value) => value + 1);

      const refreshed = await reload();

      setMessage(
        refreshed
          ? successMessage
          : `${successMessage}；畫面更新失敗，請重新整理。`
      );

      return true;
    } catch (error) {
      setError(error.message);
      return false;
    } finally {
      actionLock.current = false;
      setBusy(false);
    }
  }

  function selectMonth(nextMonth) {
    const year = nextMonth.getFullYear();

    if (!Number.isFinite(year) || year < 1900 || year > 9998) {
      return;
    }

    setMonth(nextMonth);
    setSelectedDate(`${monthText(nextMonth)}-01`);
    setSelectedBookingId(null);
  }

  function moveMonth(offset) {
    selectMonth(
      new Date(
        month.getFullYear(),
        month.getMonth() + offset,
        1,
        12
      )
    );
  }

  function goToday() {
    const value = taipeiToday();

    setMonth(new Date(`${value.slice(0, 7)}-01T12:00:00`));
    setSelectedDate(value);
    setSelectedBookingId(null);
  }

  function openWork(bookingId) {
    setSelectedBookingId(bookingId);
    setTab("work");
  }

  async function createBooking(event) {
    event.preventDefault();

    if (!caseId || !slotId || !serviceId) {
      setError("請選擇個案、服務項目與可預約時段");
      return;
    }

    await perform(async () => {
      await workRequest("/work/bookings", {
        caseId: Number(caseId),
        serviceId: Number(serviceId),
        slotId: Number(slotId),
        address,
        note,
      });

      setSlotId("");
      setNote("");
    }, "預約已送出，等待照服員確認");
  }

  async function createSlot(event) {
    event.preventDefault();

    await perform(
      () => workRequest("/slots", {
        caregiverId: Number(caregiverId),
        startAt: `${selectedDate}T${startHour}:00:00`,
      }),
      "已開放兩小時照服時段"
    );
  }

  async function changeStatus(id, status) {
    if (
      status === "CANCELLED" &&
      !window.confirm("確定要取消這筆預約嗎？")
    ) {
      return;
    }

    await perform(
      () => workRequest(
        `/bookings/${id}/status`,
        { status },
        "PATCH"
      ),
      "預約狀態已更新"
    );
  }

  async function download(id) {
    await perform(
      () => downloadBookingPdf(id),
      "PDF 已下載，可開啟後列印"
    );
  }

  async function logout() {
    if (actionLock.current) return;

    actionLock.current = true;
    setBusy(true);

    try {
      await signOut();
    } catch {
      window.alert(
        "本機已登出，但伺服器撤銷 Token 未成功，請確認後端連線。"
      );
    } finally {
      actionLock.current = false;
      onLogout();
    }
  }

  const year = month.getFullYear();
  const monthIndex = month.getMonth();

  const firstWeekday = new Date(year, monthIndex, 1).getDay();
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate();

  const cellCount =
    Math.ceil((firstWeekday + daysInMonth) / 7) * 7;

  const cells = Array.from({ length: cellCount }, (_, index) => {
    const day = index - firstWeekday + 1;

    if (day < 1 || day > daysInMonth) {
      return null;
    }

    return dateText(year, monthIndex, day);
  });

  const activeBookings = bookings.filter(
    (booking) => booking.status !== "CANCELLED"
  );

  function monthControls() {
    return (
      <div className="toolbar-actions">
        <button
          type="button"
          className="secondary"
          disabled={disabled}
          onClick={() => moveMonth(-1)}
        >
          上月
        </button>

        <input
          aria-label="選擇年月"
          type="month"
          value={monthValue}
          min="1900-01"
          max="9998-12"
          disabled={disabled}
          onChange={(event) => {
            if (event.target.value) {
              selectMonth(
                new Date(`${event.target.value}-01T12:00:00`)
              );
            }
          }}
        />

        <button
          type="button"
          className="secondary"
          disabled={disabled}
          onClick={() => moveMonth(1)}
        >
          下月
        </button>

        <button
          type="button"
          className="secondary"
          disabled={disabled}
          onClick={goToday}
        >
          本月
        </button>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="topbar">
        <div>
          <span className="brand-mark">安</span>

          <div>
            <strong>安心長照</strong>
            <small>照護預約管理</small>
          </div>
        </div>

        <div className="user-info">
          <span>
            {user.name} · {ROLE_NAMES[user.role]}
          </span>

          <button
            className="secondary"
            disabled={disabled}
            onClick={logout}
          >
            登出
          </button>
        </div>
      </header>

      <main className="content">
        <section className="welcome">
          <div>
            <span className="eyebrow">YOUR CARE SCHEDULE</span>
            <h1>每一份照顧，都值得安心安排。</h1>
            <p>
              點選日期查看預約與可用時段。
              所有服務時間以台灣時間顯示。
            </p>
          </div>

          <button
            className="secondary"
            disabled={disabled}
            onClick={() => {
              setError("");
              setMessage("");
              setRevision((value) => value + 1);
              void reload();
            }}
          >
            重新整理
          </button>
        </section>

        <section className="stats">
          <div className="stat">
            <span>本月有效預約</span>
            <strong>{activeBookings.length}</strong>
          </div>

          <div className="stat">
            <span>本月待確認</span>
            <strong>
              {bookings.filter(
                (booking) => booking.status === "PENDING"
              ).length}
            </strong>
          </div>

          <div className="stat">
            <span>本月可預約時段</span>
            <strong>{slots.length}</strong>
          </div>
        </section>

        <nav className="cw-tabs" aria-label="功能選單">
          {[
            ["calendar", "預約月曆"],
            ["cases", "個案管理"],
            [
              "work",
              user.role === "CAREGIVER"
                ? "服務紀錄"
                : "服務／核銷／支付",
            ],
          ].map(([value, text]) => (
            <button
              key={value}
              type="button"
              className={tab === value ? "cw-current" : "secondary"}
              aria-pressed={tab === value}
              disabled={disabled}
              onClick={() => setTab(value)}
            >
              {text}
            </button>
          ))}
        </nav>

        {error && (
          <p className="notice error" role="alert">
            {error}
          </p>
        )}

        {message && (
          <p className="notice success" role="status">
            {message}
          </p>
        )}

        {loading && (
          <p role="status">正在載入資料…</p>
        )}

        {tab === "calendar" ? (
          <div className="dashboard-grid">
            <section className="panel calendar-panel">
              <div className="calendar-toolbar">
                <h2>{year} 年 {monthIndex + 1} 月</h2>
                {monthControls()}
              </div>

              <div className="calendar-weekdays">
                {["日", "一", "二", "三", "四", "五", "六"].map(
                  (day) => <div key={day}>{day}</div>
                )}
              </div>

              <div className="calendar-grid">
                {cells.map((date, index) => {
                  if (!date) {
                    return (
                      <div
                        key={`empty-${index}`}
                        className="calendar-empty"
                      />
                    );
                  }

                  const bookingCount = bookings.filter(
                    (booking) =>
                      booking.startAt.slice(0, 10) === date &&
                      booking.status !== "CANCELLED"
                  ).length;

                  const slotCount = slots.filter(
                    (slot) => slot.startAt.slice(0, 10) === date
                  ).length;

                  return (
                    <button
                      key={date}
                      disabled={disabled}
                      aria-pressed={selectedDate === date}
                      className={[
                        "calendar-day",
                        date === today ? "today" : "",
                        date === selectedDate ? "selected" : "",
                      ].join(" ")}
                      onClick={() => setSelectedDate(date)}
                    >
                      <span className="day-number">
                        {Number(date.slice(8))}
                      </span>

                      {bookingCount > 0 && (
                        <span className="calendar-booking">
                          預約 {bookingCount}
                        </span>
                      )}

                      {slotCount > 0 && (
                        <span className="calendar-available">
                          可約 {slotCount}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>

              <p className="muted calendar-note">
                預約數依登入角色顯示；
                可約數是尚未被預約的未來時段。
              </p>
            </section>

            <aside className="side-column">
              <section className="panel">
                <h2>{selectedDate}</h2>
                <p className="muted">當日預約</p>

                {!loading && dayBookings.length === 0 && (
                  <div className="empty-state">
                    這一天沒有你的預約紀錄
                  </div>
                )}

                <div className="booking-list">
                  {dayBookings.map((booking) => {
                    const active = [
                      "PENDING",
                      "CONFIRMED",
                    ].includes(booking.status);

                    const canManage = [
                      "ADMIN",
                      "CAREGIVER",
                    ].includes(user.role);

                    const canCancel =
                      active &&
                      (
                        user.role === "ADMIN" ||
                        (
                          user.role === "CUSTOMER" &&
                          !timeReached(booking.startAt)
                        )
                      );

                    const flow = flows.find(
                      (item) => item.bookingId === booking.id
                    );

                    return (
                      <article
                        className="booking-card"
                        key={booking.id}
                      >
                        <div className="booking-heading">
                          <strong>{booking.serviceName}</strong>

                          <span
                            className={
                              `status ${booking.status.toLowerCase()}`
                            }
                          >
                            {STATUS_NAMES[booking.status]}
                          </span>
                        </div>

                        <p>
                          {timeText(booking.startAt)}
                          －
                          {timeText(booking.endAt)}
                        </p>

                        <p>顧客：{booking.customerName}</p>
                        <p>照服員：{booking.caregiverName}</p>

                        {flow && <p>個案：{flow.caseName}</p>}

                        <p>地址：{booking.address}</p>
                        <p>金額：NT$ {booking.price}</p>

                        {booking.note && (
                          <p>備註：{booking.note}</p>
                        )}

                        <div className="booking-actions">
                          <button
                            className="secondary"
                            disabled={disabled}
                            onClick={() => download(booking.id)}
                          >
                            PDF
                          </button>

                          {canManage &&
                            booking.status === "PENDING" && (
                              <button
                                disabled={disabled}
                                onClick={() =>
                                  changeStatus(booking.id, "CONFIRMED")
                                }
                              >
                                確認
                              </button>
                            )}

                          {canManage &&
                            booking.status === "CONFIRMED" && (
                              <button
                                disabled={
                                  disabled ||
                                  !timeReached(booking.endAt)
                                }
                                title="填寫服務紀錄後完成預約"
                                onClick={() => openWork(booking.id)}
                              >
                                完成
                              </button>
                            )}

                          {canCancel && (
                            <button
                              className="danger"
                              disabled={disabled}
                              onClick={() =>
                                changeStatus(booking.id, "CANCELLED")
                              }
                            >
                              取消
                            </button>
                          )}

                          <button
                            className="secondary"
                            disabled={disabled}
                            onClick={() => openWork(booking.id)}
                          >
                            {user.role === "CAREGIVER"
                              ? "個案／紀錄"
                              : "個案／核銷／支付"}
                          </button>
                        </div>
                      </article>
                    );
                  })}
                </div>
              </section>

              {user.role === "CUSTOMER" ? (
                <section className="panel">
                  <h2>新增預約</h2>

                  <form
                    className="form-stack"
                    onSubmit={createBooking}
                  >
                    <label>
                      照護個案
                      <select
                        value={caseId}
                        disabled={disabled}
                        onChange={(event) => {
                          const value = event.target.value;

                          setCaseId(value);

                          const item = careCases.find(
                            (c) => String(c.id) === value
                          );

                          if (item) setAddress(item.address);
                        }}
                        required
                      >
                        <option value="">請選擇個案</option>

                        {activeCases.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name}
                          </option>
                        ))}
                      </select>
                    </label>

                    {activeCases.length === 0 && (
                      <>
                        <p className="muted">
                          請先建立個案，再預約服務。
                        </p>

                        <button
                          type="button"
                          className="secondary"
                          disabled={disabled}
                          onClick={() => setTab("cases")}
                        >
                          前往個案管理
                        </button>
                      </>
                    )}

                    <label>
                      服務項目
                      <select
                        value={serviceId}
                        disabled={disabled}
                        onChange={(e) => setServiceId(e.target.value)}
                        required
                      >
                        <option value="">請選擇服務</option>

                        {services.map((service) => (
                          <option key={service.id} value={service.id}>
                            {service.name} · NT$ {service.price}
                          </option>
                        ))}
                      </select>
                    </label>

                    {selectedService && (
                      <p className="muted">
                        {selectedService.description}
                      </p>
                    )}

                    <label>
                      {selectedDate} 可預約時段
                      <select
                        value={slotId}
                        disabled={disabled}
                        onChange={(e) => setSlotId(e.target.value)}
                        required
                      >
                        <option value="">請選擇時段</option>

                        {daySlots.map((slot) => (
                          <option key={slot.id} value={slot.id}>
                            {timeText(slot.startAt)}
                            －
                            {timeText(slot.endAt)}
                            {" "}
                            {slot.caregiverName}
                          </option>
                        ))}
                      </select>
                    </label>

                    {daySlots.length === 0 && (
                      <p className="muted">
                        當日沒有可預約時段，請改選其他日期。
                      </p>
                    )}

                    <label>
                      服務地址
                      <input
                        value={address}
                        disabled={disabled}
                        onChange={(e) => setAddress(e.target.value)}
                        maxLength={200}
                        placeholder="請輸入服務地址"
                        required
                      />
                    </label>

                    <label>
                      備註
                      <textarea
                        value={note}
                        disabled={disabled}
                        onChange={(e) => setNote(e.target.value)}
                        maxLength={500}
                        rows={3}
                        placeholder="例如：抵達後請先按門鈴"
                      />
                    </label>

                    <button
                      disabled={
                        disabled || !caseId || !slotId || !serviceId
                      }
                    >
                      {busy ? "處理中…" : "送出預約"}
                    </button>
                  </form>
                </section>
              ) : (
                <section className="panel">
                  <h2>開放照服時段</h2>

                  <p className="muted">
                    日期：{selectedDate}，每次兩小時。
                  </p>

                  <form
                    className="form-stack"
                    onSubmit={createSlot}
                  >
                    {user.role === "ADMIN" ? (
                      <label>
                        照服員
                        <select
                          value={caregiverId}
                          disabled={disabled}
                          onChange={(e) => setCaregiverId(e.target.value)}
                          required
                        >
                          <option value="">請選擇照服員</option>

                          {caregivers.map((caregiver) => (
                            <option
                              key={caregiver.id}
                              value={caregiver.id}
                            >
                              {caregiver.name}
                            </option>
                          ))}
                        </select>
                      </label>
                    ) : (
                      <p>照服員：{user.name}</p>
                    )}

                    <label>
                      開始時間
                      <select
                        value={startHour}
                        disabled={disabled}
                        onChange={(e) => setStartHour(e.target.value)}
                      >
                        {Array.from(
                          { length: 11 },
                          (_, index) => index + 8
                        ).map((hour) => (
                          <option key={hour} value={pad(hour)}>
                            {pad(hour)}:00－{pad(hour + 2)}:00
                          </option>
                        ))}
                      </select>
                    </label>

                    <button disabled={disabled || !caregiverId}>
                      開放時段
                    </button>
                  </form>

                  <div className="available-list">
                    <h3>當日尚可預約</h3>

                    {daySlots.length === 0 && (
                      <p className="muted">沒有可用時段</p>
                    )}

                    {daySlots.map((slot) => (
                      <p key={slot.id}>
                        {timeText(slot.startAt)}
                        －
                        {timeText(slot.endAt)}
                        {" · "}
                        {slot.caregiverName}
                      </p>
                    ))}
                  </div>
                </section>
              )}
            </aside>
          </div>
        ) : (
          <>
            {tab === "work" && (
              <section className="panel cw-month-panel">
                <div className="calendar-toolbar">
                  <h2>{year} 年 {monthIndex + 1} 月</h2>
                  {monthControls()}
                </div>
              </section>
            )}

            <CareWorkPanel
              key={`${user.id}-${tab}-${monthValue}`}
              tab={tab}
              user={user}
              month={monthValue}
              bookings={bookings}
              flows={flows}
              careCases={careCases}
              customers={customers}
              disabled={disabled}
              revision={revision}
              selectedId={selectedBookingId}
              onSelect={setSelectedBookingId}
              onAction={perform}
            />
          </>
        )}
      </main>
    </div>
  );
}

export default function App() {
  const [user, setUser] = useState(null);
  const [starting, setStarting] = useState(true);

  useEffect(() => {
    let active = true;

    const logoutHandler = () => setUser(null);

    window.addEventListener("care:logout", logoutHandler);

    restoreSession()
      .then((restoredUser) => {
        if (active) setUser(restoredUser);
      })
      .finally(() => {
        if (active) setStarting(false);
      });

    return () => {
      active = false;
      window.removeEventListener("care:logout", logoutHandler);
    };
  }, []);

  if (starting) {
    return (
      <div className="startup">
        正在載入安心長照…
      </div>
    );
  }

  if (!user) {
    return <AuthPanel onLogin={setUser} />;
  }

  return (
    <Dashboard
      key={user.id}
      user={user}
      onLogout={() => setUser(null)}
    />
  );
}