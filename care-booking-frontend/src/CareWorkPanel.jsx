import { useEffect, useRef, useState } from "react";
import { api } from "./api";

const STATUS = {
  PENDING: "待確認",
  CONFIRMED: "已確認",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
  DRAFT: "尚未送審",
  SUBMITTED: "待審核",
  REJECTED: "已退回",
  APPROVED: "已核准",
  UNPAID: "未付款",
  REPORTED: "已登錄，待確認",
  PAID: "已收款",
  WAIVED: "免自付",
  REFUND_REQUESTED: "退款申請中",
  REFUNDED: "已退款",
};

const ACTION_NAMES = {
  CASE_LINKED: "綁定個案",
  SERVICE_RECORDED: "填寫服務紀錄",
  CLAIM_SUBMITTED: "送出核銷",
  CLAIM_REJECTED: "退回核銷",
  CLAIM_APPROVED: "核准核銷",
  DEMO_CREATED: "建立示範資料",
  "subsidy-received": "補助入帳",
  "payment-report": "登錄付款",
  "payment-confirm": "確認收款",
  "payment-reject": "退回付款登錄",
  "refund-request": "申請退款",
  "refund-confirm": "確認退款",
  "refund-reject": "退回退款申請",
};

function statusText(value) {
  return STATUS[value] || value || "—";
}

function money(value) {
  if (value == null) return "尚未核定";

  return `NT$ ${Number(value).toLocaleString("zh-TW", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

function dateTime(value) {
  return value ? value.replace("T", " ").slice(0, 16) : "—";
}

function reached(value) {
  return value
    ? Date.now() >= new Date(`${value}+08:00`).getTime()
    : false;
}

function todayTaipei() {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Taipei",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());

  const get = (type) =>
    parts.find((part) => part.type === type).value;

  return `${get("year")}-${get("month")}-${get("day")}`;
}

function choices(items, text = (item) => item.name) {
  return items.map((item) => ({
    value: item.id,
    text: text(item),
  }));
}

export async function workRequest(
  path,
  body,
  method = "POST"
) {
  return api(path, {
    method,
    ...(body === undefined
      ? {}
      : { body: JSON.stringify(body) }),
  });
}

async function downloadSettlement(id) {
  const blob = await api(
    `/work/bookings/${id}/settlement.pdf`,
    { asBlob: true }
  );

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = url;
  link.download = `settlement-${id}.pdf`;

  document.body.appendChild(link);
  link.click();
  link.remove();

  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function Control({ field }) {
  const {
    name,
    title,
    type = "text",
    value = "",
    required = true,
    options = [],
    ...attributes
  } = field;

  const props = {
    name,
    defaultValue: value ?? "",
    required,
    ...attributes,
  };

  return (
    <label>
      {title}

      {type === "select" ? (
        <select {...props}>
          <option value="">請選擇</option>

          {options.map((item) => (
            <option key={item.value} value={item.value}>
              {item.text}
            </option>
          ))}
        </select>
      ) : type === "textarea" ? (
        <textarea {...props} rows={3} />
      ) : (
        <input {...props} type={type} />
      )}
    </label>
  );
}

function WorkForm({
  fields,
  onSubmit,
  button = "儲存",
  disabled = false,
  className = "",
}) {
  return (
    <form
      className={className}
      onSubmit={(event) => {
        event.preventDefault();

        onSubmit(
          Object.fromEntries(new FormData(event.currentTarget))
        );
      }}
    >
      <fieldset
        className="cw-fieldset form-stack"
        disabled={disabled}
      >
        {fields.map((field) => (
          <Control key={field.name} field={field} />
        ))}

        <button type="submit">{button}</button>
      </fieldset>
    </form>
  );
}

function CaseEditor({
  item,
  user,
  customers,
  onSave,
  onCancel,
}) {
  return (
    <section className="panel">
      <h2>
        {item ? `修改個案：${item.name}` : "新增個案"}
      </h2>

      <WorkForm
        button="儲存個案"
        fields={[
          ...(!item && user.role === "ADMIN"
            ? [{
                name: "customerId",
                title: "所屬顧客",
                type: "select",
                options: choices(
                  customers,
                  (u) => `${u.name} · ${u.email}`
                ),
              }]
            : []),

          {
            name: "name",
            title: "個案姓名",
            value: item?.name,
            maxLength: 50,
          },
          {
            name: "birthDate",
            title: "出生日期",
            type: "date",
            value: item?.birthDate,
            max: todayTaipei(),
          },
          {
            name: "contactName",
            title: "聯絡人",
            value: item?.contactName,
            maxLength: 50,
          },
          {
            name: "contactPhone",
            title: "聯絡電話",
            value: item?.contactPhone,
            maxLength: 30,
          },
          {
            name: "address",
            title: "服務地址",
            value: item?.address,
            maxLength: 200,
          },
          {
            name: "careNeeds",
            title: "照護需求",
            type: "textarea",
            value: item?.careNeeds,
            maxLength: 1000,
          },
          {
            name: "active",
            title: "個案狀態",
            type: "select",
            value: String(item?.active ?? true),
            options: [
              { value: "true", text: "啟用" },
              { value: "false", text: "停用" },
            ],
          },
        ]}
        onSubmit={(data) => onSave({
          ...data,
          customerId: item
            ? item.customerId
            : user.role === "CUSTOMER"
              ? user.id
              : Number(data.customerId),
          active: data.active === "true",
        })}
      />

      <div className="cw-actions">
        <button
          type="button"
          className="secondary"
          onClick={onCancel}
        >
          取消編輯
        </button>
      </div>
    </section>
  );
}

function WorkDetail({
  work,
  user,
  onAction,
  onHistory,
}) {
  const finance = work.finance;
  const admin = user.role === "ADMIN";
  const root = `/work/bookings/${work.bookingId}`;

  function financeForm(action, title, placeholder) {
    return (
      <WorkForm
        key={action}
        className="cw-subpanel"
        button={title}
        fields={[{
          name: "text",
          title,
          placeholder,
          maxLength: 200,
        }]}
        onSubmit={(body) =>
          onAction(
            () => workRequest(`${root}/finance/${action}`, body),
            `${title}已完成`
          )
        }
      />
    );
  }

  return (
    <section className="panel cw-detail">
      <div className="cw-heading">
        <h2>預約 #{work.bookingId} · {work.caseName}</h2>

        <span
          className={
            `status ${work.bookingStatus.toLowerCase()}`
          }
        >
          {statusText(work.bookingStatus)}
        </span>
      </div>

      <div className="cw-info-grid">
        <p>顧客：{work.customerName}</p>
        <p>照服員：{work.caregiverName}</p>
        <p>服務：{work.serviceName}</p>
        <p>費用：{money(work.price)}</p>
      </div>

      <p>
        預約時間：{dateTime(work.startAt)}
        {" ～ "}
        {dateTime(work.endAt)}
      </p>

      <div className="cw-section">
        <h3>服務紀錄</h3>

        <p>
          實際時間：{dateTime(work.actualStart)}
          {" ～ "}
          {dateTime(work.actualEnd)}
        </p>

        <p className="cw-preserve">
          {work.serviceNote || "尚未填寫服務紀錄"}
        </p>

        {work.recordEditable && (
          <>
            <WorkForm
              button="儲存紀錄並完成服務"
              disabled={!reached(work.endAt)}
              fields={[
                {
                  name: "actualStart",
                  title: "實際開始時間",
                  type: "datetime-local",
                  step: 60,
                  value: (
                    work.actualStart || work.startAt
                  ).slice(0, 16),
                },
                {
                  name: "actualEnd",
                  title: "實際結束時間",
                  type: "datetime-local",
                  step: 60,
                  value: (
                    work.actualEnd || work.endAt
                  ).slice(0, 16),
                },
                {
                  name: "serviceNote",
                  title: "服務內容",
                  type: "textarea",
                  value: work.serviceNote,
                  maxLength: 2000,
                },
              ]}
              onSubmit={(body) =>
                onAction(
                  () => workRequest(`${root}/record`, body),
                  "服務紀錄已儲存，預約已完成"
                )
              }
            />

            {!reached(work.endAt) && (
              <p className="muted">
                預約時段結束後才能儲存完成紀錄。
              </p>
            )}
          </>
        )}
      </div>

      {finance && (
        <>
          <div className="cw-section">
            <h3>補助核銷</h3>

            <p>
              本筆補助比例：{finance.subsidyRate}%；
              每筆上限：{money(finance.subsidyLimit)}
            </p>

            <div className="cw-info-grid">
              <p>核銷狀態：{statusText(finance.claimStatus)}</p>
              <p>申請補助：{money(finance.requestedAmount)}</p>
              <p>核准補助：{money(finance.approvedAmount)}</p>
              <p>顧客自付：{money(finance.customerAmount)}</p>
            </div>

            {finance.reviewNote && (
              <p className="cw-preserve">
                審核說明：{finance.reviewNote}
              </p>
            )}

            {["DRAFT", "REJECTED"].includes(finance.claimStatus) && (
              <button
                type="button"
                disabled={
                  work.bookingStatus !== "COMPLETED" ||
                  !work.serviceNote
                }
                onClick={() =>
                  onAction(
                    () => workRequest(`${root}/submit`),
                    "核銷已送出，等待管理者審核"
                  )
                }
              >
                {finance.claimStatus === "REJECTED"
                  ? "重新送審"
                  : "送出核銷"}
              </button>
            )}

            {admin && finance.claimStatus === "SUBMITTED" && (
              <WorkForm
                className="cw-subpanel"
                button="送出審核結果"
                fields={[
                  {
                    name: "approve",
                    title: "審核結果",
                    type: "select",
                    value: "true",
                    options: [
                      { value: "true", text: "核准" },
                      { value: "false", text: "退回補件" },
                    ],
                  },
                  {
                    name: "amount",
                    title: "核准金額（退回時可留空）",
                    type: "number",
                    value: finance.requestedAmount,
                    min: 0,
                    max: finance.requestedAmount,
                    step: "0.01",
                    required: false,
                  },
                  {
                    name: "reason",
                    title: "審核說明",
                    type: "textarea",
                    maxLength: 500,
                  },
                ]}
                onSubmit={(body) =>
                  onAction(
                    () => workRequest(`${root}/review`, {
                      approve: body.approve === "true",
                      amount:
                        body.approve === "true" && body.amount !== ""
                          ? Number(body.amount)
                          : null,
                      reason: body.reason,
                    }),
                    "核銷審核結果已儲存"
                  )
                }
              />
            )}

            {finance.claimStatus === "APPROVED" && (
              <>
                <p className="cw-spaced">
                  補助款入帳：
                  {finance.subsidyReceivedAt
                    ? dateTime(finance.subsidyReceivedAt)
                    : Number(finance.approvedAmount) > 0
                      ? "尚未登錄"
                      : "無應收補助"}
                </p>

                {finance.subsidyReference && (
                  <p>補助入帳編號：{finance.subsidyReference}</p>
                )}

                {admin &&
                  Number(finance.approvedAmount) > 0 &&
                  !finance.subsidyReceivedAt &&
                  financeForm(
                    "subsidy-received",
                    "登錄補助入帳",
                    "補助撥款或銀行交易編號"
                  )}
              </>
            )}
          </div>

          {finance.claimStatus === "APPROVED" && (
            <div className="cw-section">
              <h3>顧客支付</h3>

              <p>
                付款狀態：{statusText(finance.paymentStatus)}
              </p>

              <p>自付金額：{money(finance.customerAmount)}</p>

              {finance.paymentReference && (
                <p>付款資訊：{finance.paymentReference}</p>
              )}

              {finance.paidAt && (
                <p>確認收款時間：{dateTime(finance.paidAt)}</p>
              )}

              {finance.paymentStatus === "UNPAID" &&
                financeForm(
                  "payment-report",
                  "登錄付款",
                  "轉帳日期、交易編號或帳號末五碼"
                )}

              {admin && finance.paymentStatus === "REPORTED" && (
                <>
                  {financeForm(
                    "payment-confirm",
                    "確認已收款",
                    "核對後的銀行交易編號"
                  )}

                  {financeForm(
                    "payment-reject",
                    "退回付款登錄",
                    "例如：尚未查到款項"
                  )}
                </>
              )}

              {finance.paymentStatus === "PAID" &&
                financeForm(
                  "refund-request",
                  "申請退還全部自付款",
                  "退款原因"
                )}

              {finance.refundReference && (
                <p>退款資訊：{finance.refundReference}</p>
              )}

              {admin &&
                finance.paymentStatus === "REFUND_REQUESTED" && (
                  <>
                    {financeForm(
                      "refund-confirm",
                      "確認已完成退款",
                      "實際退款交易編號"
                    )}

                    {financeForm(
                      "refund-reject",
                      "退回退款申請",
                      "退回原因"
                    )}
                  </>
                )}

              {finance.refundedAt && (
                <p>
                  已退還 {money(finance.customerAmount)}
                  {" ／ "}
                  {dateTime(finance.refundedAt)}
                </p>
              )}

              <p className="muted">
                此處記錄人工收退款結果，不會自動扣款或轉帳。
                退款僅處理顧客自付額。
              </p>

              <button
                type="button"
                className="secondary"
                onClick={() =>
                  onAction(
                    () => downloadSettlement(work.bookingId),
                    "費用結算 PDF 已下載"
                  )
                }
              >
                下載費用結算 PDF
              </button>
            </div>
          )}

          <div className="cw-actions">
            <button
              type="button"
              className="secondary"
              onClick={onHistory}
            >
              查看操作歷程
            </button>
          </div>
        </>
      )}
    </section>
  );
}

export default function CareWorkPanel({
  tab,
  user,
  month,
  bookings,
  flows,
  careCases,
  customers,
  disabled,
  revision,
  selectedId,
  onSelect,
  onAction,
}) {
  const admin = user.role === "ADMIN";
  const caregiver = user.role === "CAREGIVER";

  const [editing, setEditing] = useState(null);
  const [history, setHistory] = useState(null);
  const [historyBusy, setHistoryBusy] = useState(false);
  const [historyError, setHistoryError] = useState("");

  const historyVersion = useRef(0);

  useEffect(() => {
    historyVersion.current += 1;
    setHistory(null);
    setHistoryError("");
    setHistoryBusy(false);

    return () => {
      historyVersion.current += 1;
    };
  }, [selectedId, revision]);

  const activeCases = careCases.filter((item) => item.active);

  const selectedBooking = bookings.find(
    (item) => item.id === selectedId
  );

  const work = flows.find(
    (item) => item.bookingId === selectedId
  );

  async function loadHistory() {
    if (!work) return;

    const version = ++historyVersion.current;

    setHistoryBusy(true);
    setHistoryError("");

    try {
      const result = await api(
        `/work/bookings/${work.bookingId}/history`
      );

      if (version === historyVersion.current) {
        setHistory(result);
      }
    } catch (error) {
      if (version === historyVersion.current) {
        setHistoryError(error.message);
      }
    } finally {
      if (version === historyVersion.current) {
        setHistoryBusy(false);
      }
    }
  }

  return (
    <div className="cw-module">
      {historyError && (
        <p className="notice error" role="alert">
          {historyError}
        </p>
      )}

      {historyBusy && (
        <p className="muted" role="status">
          正在讀取操作歷程…
        </p>
      )}

      <fieldset
        className="cw-fieldset cw-stack"
        disabled={disabled || historyBusy}
      >
        {tab === "cases" ? (
          <>
            <section className="panel">
              <div className="cw-heading">
                <div>
                  <h2>個案管理</h2>
                  <p className="muted cw-no-bottom">
                    {caregiver
                      ? "查看分派給你的服務所關聯的個案。"
                      : "管理被照顧者資料、聯絡方式與照護需求。"}
                  </p>
                </div>

                {!caregiver && (
                  <button
                    type="button"
                    onClick={() => setEditing("new")}
                  >
                    新增個案
                  </button>
                )}
              </div>
            </section>

            {editing !== null && !caregiver && (
              <CaseEditor
                key={`${editing}-${revision}`}
                item={
                  editing === "new"
                    ? null
                    : careCases.find((item) => item.id === editing)
                }
                user={user}
                customers={customers}
                onCancel={() => setEditing(null)}
                onSave={async (body) => {
                  const saved = await onAction(
                    () => workRequest(
                      editing === "new"
                        ? "/work/cases"
                        : `/work/cases/${editing}`,
                      body,
                      editing === "new" ? "POST" : "PATCH"
                    ),
                    "個案資料已儲存"
                  );

                  if (saved) setEditing(null);
                }}
              />
            )}

            {careCases.length === 0 && (
              <section className="panel">
                <div className="empty-state">
                  目前沒有可查看的個案
                </div>
              </section>
            )}

            <div className="cw-case-grid">
              {careCases.map((item) => (
                <article className="panel cw-detail" key={item.id}>
                  <div className="cw-heading">
                    <h2>{item.name}</h2>

                    <span
                      className={
                        `status ${item.active ? "confirmed" : "cancelled"}`
                      }
                    >
                      {item.active ? "啟用" : "停用"}
                    </span>
                  </div>

                  <p>所屬顧客：{item.customerName}</p>
                  <p>出生日期：{item.birthDate}</p>
                  <p>聯絡人：{item.contactName}</p>
                  <p>電話：{item.contactPhone}</p>
                  <p>地址：{item.address}</p>

                  <p className="cw-preserve">
                    照護需求：{item.careNeeds}
                  </p>

                  {!caregiver && (
                    <>
                      <p>
                        補助比例：{item.subsidyRate}%；
                        每筆上限：{money(item.subsidyLimit)}
                      </p>

                      <button
                        type="button"
                        className="secondary"
                        onClick={() => setEditing(item.id)}
                      >
                        修改個案
                      </button>
                    </>
                  )}

                  {admin && (
                    <>
                      <WorkForm
                        key={`policy-${item.id}-${revision}`}
                        className="cw-subpanel"
                        button="儲存補助設定"
                        fields={[
                          {
                            name: "subsidyRate",
                            title: "補助比例（%）",
                            type: "number",
                            value: item.subsidyRate,
                            min: 0,
                            max: 100,
                            step: "0.01",
                          },
                          {
                            name: "subsidyLimit",
                            title: "每筆預約補助上限",
                            type: "number",
                            value: item.subsidyLimit,
                            min: 0,
                            max: "99999999.99",
                            step: "0.01",
                          },
                        ]}
                        onSubmit={(body) =>
                          onAction(
                            () => workRequest(
                              `/work/cases/${item.id}/policy`,
                              {
                                subsidyRate: Number(body.subsidyRate),
                                subsidyLimit: Number(body.subsidyLimit),
                              },
                              "PATCH"
                            ),
                            "補助設定已更新"
                          )
                        }
                      />

                      <p className="muted cw-no-bottom">
                        新設定只套用於之後建立或綁定的預約。
                      </p>
                    </>
                  )}
                </article>
              ))}
            </div>
          </>
        ) : (
          <>
            <section className="panel">
              <h2>
                {month} ·
                {caregiver ? "服務紀錄" : "服務、核銷與支付"}
              </h2>

              <p className="muted">
                選擇預約查看詳細資料。
                舊預約尚未綁定個案時，也可以在這裡完成綁定。
              </p>

              <div className="cw-table-wrap">
                <table className="cw-table">
                  <thead>
                    <tr>
                      <th>預約／日期</th>
                      <th>顧客／個案</th>
                      <th>服務狀態</th>
                      {!caregiver && <th>核銷</th>}
                      {!caregiver && <th>支付</th>}
                      <th>操作</th>
                    </tr>
                  </thead>

                  <tbody>
                    {bookings.map((booking) => {
                      const flow = flows.find(
                        (item) => item.bookingId === booking.id
                      );

                      return (
                        <tr key={booking.id}>
                          <td>
                            #{booking.id}
                            <br />
                            {dateTime(booking.startAt)}
                            <br />
                            <span className="muted">
                              {booking.serviceName}
                            </span>
                          </td>

                          <td>
                            {booking.customerName}
                            <br />
                            {flow?.caseName || "尚未綁定個案"}
                          </td>

                          <td>
                            <span
                              className={
                                `status ${booking.status.toLowerCase()}`
                              }
                            >
                              {statusText(booking.status)}
                            </span>
                          </td>

                          {!caregiver && (
                            <td>
                              {flow
                                ? statusText(flow.finance?.claimStatus)
                                : "—"}
                            </td>
                          )}

                          {!caregiver && (
                            <td>
                              {flow?.finance?.claimStatus === "APPROVED"
                                ? statusText(flow.finance.paymentStatus)
                                : "待核定"}
                            </td>
                          )}

                          <td>
                            <button
                              type="button"
                              className="secondary"
                              onClick={() => onSelect(booking.id)}
                            >
                              開啟
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {bookings.length === 0 && (
                <div className="empty-state">
                  這個月份沒有預約資料
                </div>
              )}
            </section>

            {selectedBooking && !work && (
              <section className="panel cw-detail">
                <h2>預約 #{selectedBooking.id} · 綁定個案</h2>
                <p>顧客：{selectedBooking.customerName}</p>
                <p>服務：{selectedBooking.serviceName}</p>

                {selectedBooking.status === "CANCELLED" ? (
                  <p className="muted">
                    已取消的預約不能綁定個案。
                  </p>
                ) : caregiver ? (
                  <p className="muted">
                    請先由顧客或管理者綁定個案，
                    完成後即可填寫服務紀錄。
                  </p>
                ) : (
                  <>
                    <WorkForm
                      key={`link-${selectedBooking.id}-${revision}`}
                      button="綁定個案"
                      disabled={activeCases.length === 0}
                      fields={[{
                        name: "caseId",
                        title: "選擇個案",
                        type: "select",
                        options: choices(
                          activeCases,
                          (item) =>
                            `${item.customerName} ／ ${item.name}`
                        ),
                      }]}
                      onSubmit={(body) =>
                        onAction(
                          () => workRequest(
                            `/work/bookings/${selectedBooking.id}/link`,
                            { caseId: Number(body.caseId) }
                          ),
                          "預約已綁定個案"
                        )
                      }
                    />

                    <p className="muted cw-spaced">
                      請選擇與預約屬於同一位顧客的個案。
                      綁定後會保存目前的補助設定，且不能改換個案。
                    </p>

                    {activeCases.length === 0 && (
                      <p className="muted">
                        請先到「個案管理」建立啟用中的個案。
                      </p>
                    )}
                  </>
                )}
              </section>
            )}

            {work && (
              <WorkDetail
                key={`${work.bookingId}-${revision}`}
                work={work}
                user={user}
                onAction={onAction}
                onHistory={loadHistory}
              />
            )}

            {history && (
              <section className="panel">
                <h2>操作歷程</h2>

                {history.length === 0 && (
                  <div className="empty-state">
                    尚無操作歷程
                  </div>
                )}

                {history.map((item) => (
                  <article className="cw-history" key={item.id}>
                    <strong>
                      {ACTION_NAMES[item.action] || item.action}
                    </strong>

                    <p className="muted">
                      {dateTime(item.createdAt)}
                      {" · "}
                      {item.actorName}
                    </p>

                    <p className="cw-preserve">{item.detail}</p>
                  </article>
                ))}
              </section>
            )}
          </>
        )}
      </fieldset>
    </div>
  );
}