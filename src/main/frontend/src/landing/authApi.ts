/**
 * What this shell still talks to.
 *
 * Only the endpoints a visitor reaches before there is a session: signing in, registering,
 * and the three links that arrive by email. Everything else - accounts, categories,
 * transactions, transfers, currencies, banks, CSV import - moved to the Compose client at
 * /app/, which has its own typed client over the same API.
 */

const BASE = '/api';

export interface InvitePreview {
    householdName: string;
    invitedBy?: string | null;
    expiresAt: string;
}

/** A refusal from the server, with the status so a page can tell "unknown" from "expired". */
export class AuthApiError extends Error {
    constructor(message: string, public readonly status: number) {
        super(message);
        this.name = 'AuthApiError';
    }
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
    const res = await fetch(BASE + url, {
        headers: {
            'Content-Type': 'application/json',
            ...(options?.headers as Record<string, string>),
        },
        ...options,
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({ message: res.statusText }));
        throw new AuthApiError(err.message || res.statusText, res.status);
    }
    if (res.status === 204) return undefined as unknown as T;
    return res.json();
}

// ─── Auth ───────────────────────────────────────────────────────────────────
export const authApi = {
    login: (data: any) => request<any>('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
    /** `data.inviteToken` lets the registration through on a closed instance, into that household. */
    register: (data: any) => request<any>('/auth/register', { method: 'POST', body: JSON.stringify(data) }),

    verifyEmail: (token: string) =>
        request<{ message: string }>('/auth/verify-email', { method: 'POST', body: JSON.stringify({ token }) }),

    /** Always resolves, even for an unknown address - see the note on the server. */
    forgotPassword: (email: string) =>
        request<{ message: string }>('/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) }),

    resetPassword: (token: string, newPassword: string) =>
        request<{ message: string }>('/auth/reset-password', {
            method: 'POST', body: JSON.stringify({ token, newPassword }),
        }),

    /** Public: what an invitation link is for, shown before anyone has signed in. */
    invitePreview: (token: string) => request<InvitePreview>(`/invites/${encodeURIComponent(token)}`),
};
