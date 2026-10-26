/**
 * CineBook Centralized Fetch API & UI Utilities Module
 * Handles JWT authentication headers/cookies, standard error parsing, XSS escaping,
 * currency/date formatting, and deterministic QR SVG generation.
 */

const TOKEN_STORAGE_KEY = 'cinebook_jwt_token';
const USER_STORAGE_KEY = 'cinebook_user_profile';
const CITY_STORAGE_KEY = 'cinebook_selected_city';

export function getAuthToken() {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function getStoredUser() {
    try {
        const raw = localStorage.getItem(USER_STORAGE_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

export function setAuthSession(token, user) {
    if (token) {
        localStorage.setItem(TOKEN_STORAGE_KEY, token);
    }
    if (user) {
        localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
    }
}

export function clearAuthSession() {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
}

export function getSelectedCity() {
    return localStorage.getItem(CITY_STORAGE_KEY) || 'Bengaluru';
}

export function setSelectedCity(city) {
    localStorage.setItem(CITY_STORAGE_KEY, city);
    window.dispatchEvent(new CustomEvent('cinebook:cityChanged', { detail: { city } }));
}

export function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

export function formatCurrency(amount) {
    const num = Number(amount || 0);
    return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(num);
}

export function formatDate(isoStringOrDate) {
    if (!isoStringOrDate) return '';
    const d = new Date(isoStringOrDate);
    if (Number.isNaN(d.getTime())) return String(isoStringOrDate);
    return d.toLocaleDateString('en-IN', {
        weekday: 'short',
        day: '2-digit',
        month: 'short',
        year: 'numeric'
    });
}

export function formatTime(isoString) {
    if (!isoString) return '';
    const d = new Date(isoString);
    if (Number.isNaN(d.getTime())) return String(isoString);
    return d.toLocaleTimeString('en-IN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true
    });
}

export function formatDateTime(isoString) {
    if (!isoString) return '';
    return `${formatDate(isoString)} • ${formatTime(isoString)}`;
}

export function getLocalIsoDate(offsetDays = 0) {
    const d = new Date();
    d.setDate(d.getDate() + offsetDays);
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
}

export function showToast(message, type = 'info') {
    let container = document.getElementById('cb-toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'cb-toast-container';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'status');
    toast.innerHTML = `<span>${escapeHtml(message)}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.remove();
    }, 4200);
}

/**
 * Core HTTP request wrapper around Fetch API.
 */
export async function apiRequest(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (!headers.has('Accept')) {
        headers.set('Accept', 'application/json');
    }
    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
        headers.set('Content-Type', 'application/json');
    }
    const token = getAuthToken();
    if (token && !headers.has('Authorization')) {
        headers.set('Authorization', `Bearer ${token}`);
    }

    const fetchOptions = {
        method: options.method || 'GET',
        headers,
        credentials: 'include',
        body: options.body && typeof options.body === 'object' && !(options.body instanceof FormData)
            ? JSON.stringify(options.body)
            : options.body
    };

    const response = await fetch(path, fetchOptions);

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get('content-type') || '';
    let payload = null;
    if (contentType.includes('application/json')) {
        payload = await response.json();
    } else {
        payload = await response.text();
    }

    if (!response.ok) {
        if (response.status === 401 && !path.includes('/api/auth/login')) {
            clearAuthSession();
        }
        const errMessage = (payload && typeof payload === 'object' && payload.message)
            ? payload.message
            : `Request failed with status ${response.status}`;
        const err = new Error(errMessage);
        err.status = response.status;
        err.payload = payload;
        throw err;
    }

    return payload;
}

export const apiGet = (path) => apiRequest(path, { method: 'GET' });
export const apiPost = (path, body, extraHeaders = {}) =>
    apiRequest(path, { method: 'POST', body, headers: extraHeaders });
export const apiPut = (path, body) => apiRequest(path, { method: 'PUT', body });
export const apiPatch = (path, body) => apiRequest(path, { method: 'PATCH', body });
export const apiDelete = (path) => apiRequest(path, { method: 'DELETE' });

/**
 * Generates a deterministic, scannable-style SVG QR matrix representation
 * for the non-sensitive ticket verification payload.
 */
export function generateQrSvg(payloadText, size = 168) {
    const grid = 21;
    const cell = size / grid;
    let hash = 2166136261;
    const str = String(payloadText || 'CINEBOOK');

    const isFinderPattern = (r, c) => {
        const inTopLeft = r < 7 && c < 7;
        const inTopRight = r < 7 && c >= grid - 7;
        const inBottomLeft = r >= grid - 7 && c < 7;
        return inTopLeft || inTopRight || inBottomLeft;
    };

    const finderCellFilled = (r, c, r0, c0) => {
        const lr = r - r0;
        const lc = c - c0;
        if (lr === 0 || lr === 6 || lc === 0 || lc === 6) return true;
        if (lr >= 2 && lr <= 4 && lc >= 2 && lc <= 4) return true;
        return false;
    };

    let rects = '';
    for (let r = 0; r < grid; r++) {
        for (let c = 0; c < grid; c++) {
            let filled = false;
            if (r < 7 && c < 7) {
                filled = finderCellFilled(r, c, 0, 0);
            } else if (r < 7 && c >= grid - 7) {
                filled = finderCellFilled(r, c, 0, grid - 7);
            } else if (r >= grid - 7 && c < 7) {
                filled = finderCellFilled(r, c, grid - 7, 0);
            } else if (!isFinderPattern(r, c)) {
                const charCode = str.charCodeAt((r * grid + c) % str.length);
                hash ^= charCode + ((r * 31) ^ (c * 17));
                hash = Math.imul(hash, 16777619);
                filled = (Math.abs(hash) % 10) < 5;
            }
            if (filled) {
                rects += `<rect x="${(c * cell).toFixed(1)}" y="${(r * cell).toFixed(1)}" width="${cell.toFixed(1)}" height="${cell.toFixed(1)}" fill="#0F172A"/>`;
            }
        }
    }

    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" role="img" aria-label="Ticket Verification QR Code">
        <rect width="${size}" height="${size}" fill="#FFFFFF" rx="8"/>
        ${rects}
    </svg>`;
}
