const BASE_URL = "http://localhost:8080/api";

let accessToken = "";
let refreshPromise = null;

function storeTokens(data) {
  accessToken = data.accessToken;

  sessionStorage.setItem(
    "care_refresh",
    data.refreshToken
  );

  return data.user;
}

export function clearSession() {
  accessToken = "";
  sessionStorage.removeItem("care_refresh");
}

async function readError(response) {
  const data = await response.json().catch(() => ({}));

  return new Error(
    data.message || `請求失敗：HTTP ${response.status}`
  );
}

async function authRequest(path, body) {
  const response = await fetch(
    `${BASE_URL}/auth${path}`,
    {
      method: "POST",

      headers: {
        "Content-Type": "application/json",
      },

      body: JSON.stringify(body),
    }
  );

  if (!response.ok) {
    throw await readError(response);
  }

  return response.json();
}

export async function signIn(email, password) {
  const data = await authRequest("/login", {
    email,
    password,
  });

  return storeTokens(data);
}

export async function signUp(name, email, password) {
  const data = await authRequest("/register", {
    name,
    email,
    password,
  });

  return storeTokens(data);
}

export async function refreshSession() {
  // 多個 API 同時 401，只送出一次 refresh
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const refreshToken = sessionStorage.getItem(
        "care_refresh"
      );

      if (!refreshToken) {
        throw new Error("請重新登入");
      }

      try {
        const data = await authRequest("/refresh", {
          refreshToken,
        });

        return storeTokens(data);
      } catch (error) {
        clearSession();
        window.dispatchEvent(new Event("care:logout"));
        throw error;
      }
    })().finally(() => {
      refreshPromise = null;
    });
  }

  return refreshPromise;
}

export async function restoreSession() {
  if (!sessionStorage.getItem("care_refresh")) {
    return null;
  }

  try {
    return await refreshSession();
  } catch {
    return null;
  }
}

export async function signOut() {
  // 若剛好正在輪替 Token，先取得最新的一枚再撤銷
  if (refreshPromise) {
    await refreshPromise.catch(() => {});
  }

  const refreshToken = sessionStorage.getItem(
    "care_refresh"
  );

  try {
    if (refreshToken) {
      await authRequest("/logout", {
        refreshToken,
      });
    }
  } finally {
    clearSession();
  }
}

export async function api(path, options = {}) {
  async function send() {
    const headers = {
      ...options.headers,
    };

    if (options.body) {
      headers["Content-Type"] = "application/json";
    }

    if (accessToken) {
      headers.Authorization = `Bearer ${accessToken}`;
    }

    return fetch(`${BASE_URL}${path}`, {
      ...options,
      headers,
    });
  }

  let response = await send();

  if (
    response.status === 401 &&
    sessionStorage.getItem("care_refresh")
  ) {
    await refreshSession();
    response = await send();
  }

  if (!response.ok) {
    if (response.status === 401) {
      clearSession();
      window.dispatchEvent(new Event("care:logout"));
    }

    throw await readError(response);
  }

  if (options.asBlob) {
    return response.blob();
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export async function downloadBookingPdf(id) {
  const blob = await api(`/bookings/${id}/pdf`, {
    asBlob: true,
  });

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = url;
  link.download = `booking-${id}.pdf`;

  document.body.appendChild(link);
  link.click();
  link.remove();

  setTimeout(() => URL.revokeObjectURL(url), 1000);
}