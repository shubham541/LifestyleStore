const TOKEN_KEY = 'ls.token';

export class ApiError extends Error {
    constructor(message, status, fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors || {};
    }
}

// sessionStorage: cleared when the tab closes. The strict CSP (no inline/3rd-party script) is the main XSS defence.
const read = () => { try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; } };

function claims(token) {
    try {
        return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    } catch {
        return null;
    }
}

export function isLoggedIn() {
    const token = read();
    const c = token && claims(token);
    return Boolean(c && c.exp * 1000 > Date.now());
}

export function setToken(token) { try { sessionStorage.setItem(TOKEN_KEY, token); } catch { /* storage blocked */ } }
export function logout() { try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ } }

export async function api(path, { method = 'GET', body } = {}) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const token = read();
    if (token) headers.Authorization = `Bearer ${token}`;

    const res = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await res.text();
    const data = text ? safeJson(text) : null;

    if (!res.ok) {
        if (res.status === 401 && token) { logout(); window.dispatchEvent(new Event('ls:auth')); }
        const message = res.status === 429 ? 'Too many requests, please wait a minute.'
            : (data && (data.detail || data.title)) || 'Something went wrong';
        throw new ApiError(message, res.status, data && data.errors);
    }
    return data;
}

function safeJson(text) {
    try { return JSON.parse(text); } catch { return null; }
}
