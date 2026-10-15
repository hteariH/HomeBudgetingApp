import { session } from './session';

const BASE = '/api';

/** Requests that mint tokens. The bearer header must never ride along on these. */
const TOKEN_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh'];

/**
 * Statuses whose `message` the server writes for a person to read. 500 is left out on
 * purpose: its message is a raw exception, which belongs in a log, not on a screen.
 */
const USER_FACING = new Set([400, 404, 409, 422, 502]);

export class ApiError extends Error {
    constructor(
        public readonly status: number,
        public readonly serverMessage: string | null,
    ) {
        super(serverMessage ?? `HTTP ${status}`);
        this.name = 'ApiError';
    }
}

export async function apiErrorFrom(res: Response): Promise<ApiError> {
    let message: string | null = null;
    if (USER_FACING.has(res.status)) {
        try {
            const body = JSON.parse(await res.text());
            if (typeof body?.message === 'string' && body.message.trim()) message = body.message;
        } catch {
            // Not the server's error shape - a proxy page, say. The caller shows its own text.
        }
    }
    return new ApiError(res.status, message);
}

export type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    json?: unknown;
    query?: Record<string, QueryValue>;
    form?: FormData;
}

// One refresh at a time. Every refusal that lands while it runs awaits the same promise, so a
// burst of requests spends exactly one refresh token - what the Ktor Auth plugin did for
// the Compose client. Spending it twice would have the server revoke the family.
let refreshing: Promise<boolean> | null = null;

function refreshOnce(): Promise<boolean> {
    if (!refreshing) {
        refreshing = (async () => {
            const refresh = session.get()?.refresh;
            if (!refresh) {
                session.clear();
                return false;
            }
            try {
                const res = await fetch(`${BASE}/auth/refresh`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ refreshToken: refresh }),
                });
                if (!res.ok) throw new Error(String(res.status));
                session.save(await res.json());
                return true;
            } catch {
                // A revoked or expired refresh token is terminal. Clearing the session is what
                // sends the UI back to sign-in instead of retrying forever.
                session.clear();
                return false;
            }
        })().finally(() => {
            refreshing = null;
        });
    }
    return refreshing;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(query ?? {})) {
        if (v !== null && v !== undefined && v !== '') qs.set(k, String(v));
    }
    const s = qs.toString();
    return `${BASE}${path}${s ? `?${s}` : ''}`;
}

function send(path: string, opts: RequestOptions): Promise<Response> {
    const headers: Record<string, string> = {};
    if (opts.json !== undefined) headers['Content-Type'] = 'application/json';
    const token = session.get()?.access;
    if (token && !TOKEN_ENDPOINTS.includes(path)) headers.Authorization = `Bearer ${token}`;
    return fetch(buildUrl(path, opts.query), {
        method: opts.method ?? 'GET',
        headers,
        body: opts.form ?? (opts.json !== undefined ? JSON.stringify(opts.json) : undefined),
    });
}

/**
 * Whether a refusal may just mean the access token has expired. The server has no
 * authentication entry point configured, so Spring answers an expired or garbled token with
 * 403, not 401 - and a client that refreshed on 401 only (the Ktor plugin did) never renewed
 * the session at all. A genuine 403 costs one refresh and still comes back as a 403.
 */
const mayBeExpired = (status: number, path: string) =>
    (status === 401 || status === 403) && !TOKEN_ENDPOINTS.includes(path) && session.get() !== null;

/** Calls the backend. Throws [ApiError] on any non-2xx, after one refresh if the token looked stale. */
export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
    let res = await send(path, opts);
    if (mayBeExpired(res.status, path) && (await refreshOnce())) {
        res = await send(path, opts);
    }
    if (!res.ok) throw await apiErrorFrom(res);
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    return (text ? JSON.parse(text) : undefined) as T;
}
