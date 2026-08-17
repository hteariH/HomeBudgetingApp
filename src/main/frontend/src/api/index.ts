import {
    Account, BankConnection, BankProvider, CsvMapping, CsvPreview, CsvRow,
    ImportResult, PendingImport, RemoteAccount,
    Category, CategoryType, TransactionType, CreateAccountRequest, CreateCategoryRequest,
    CreateCurrencyRequest, CreateTransactionRequest, CreateTransferRequest,
    Currency, PagedResponse, Transaction, Transfer,
    UpdateAccountRequest, UpdateCategoryRequest, UpdateCurrencyRequest
} from '../types';

const BASE = '/api';

function getAuthHeader(): Record<string, string> {
    const token = localStorage.getItem('token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
    const res = await fetch(BASE + url, {
        headers: { 
            'Content-Type': 'application/json', 
            ...getAuthHeader(),
            ...(options?.headers as Record<string, string>)
        },
        ...options,
    });
    if (res.status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
        throw new Error('Unauthorized');
    }
    if (!res.ok) {
        const err = await res.json().catch(() => ({ message: res.statusText }));
        throw new Error(err.message || res.statusText);
    }
    if (res.status === 204) return undefined as unknown as T;
    return res.json();
}

// ─── Auth ───────────────────────────────────────────────────────────────────
export const authApi = {
    login: (data: any) => request<any>('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
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
};

// ─── Users ──────────────────────────────────────────────────────────────────
export const userApi = {
    getMe: () => request<any>('/users/me'),
    updateBaseCurrency: (currencyId: number) => request<any>(`/users/me/base-currency?currencyId=${currencyId}`, { method: 'PUT' }),
};

// ─── Currencies ───────────────────────────────────────────────────────────────
export const currencyApi = {
    getAll: () => request<Currency[]>('/currencies'),
    create: (data: CreateCurrencyRequest) => request<Currency>('/currencies', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: UpdateCurrencyRequest) => request<Currency>(`/currencies/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) => request<void>(`/currencies/${id}`, { method: 'DELETE' }),
};

// ─── Accounts ─────────────────────────────────────────────────────────────────
export const accountApi = {
    getAll: () => request<Account[]>('/accounts'),
    getById: (id: number) => request<Account>(`/accounts/${id}`),
    create: (data: CreateAccountRequest) => request<Account>('/accounts', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: UpdateAccountRequest) => request<Account>(`/accounts/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) => request<void>(`/accounts/${id}`, { method: 'DELETE' }),
};

// ─── Categories ───────────────────────────────────────────────────────────────
export const categoryApi = {
    getAll: (type?: CategoryType) => request<Category[]>(`/categories${type ? `?type=${type}` : ''}`),
    create: (data: CreateCategoryRequest) => request<Category>('/categories', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: UpdateCategoryRequest) => request<Category>(`/categories/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) => request<void>(`/categories/${id}`, { method: 'DELETE' }),
};

// ─── Transactions ─────────────────────────────────────────────────────────────
export const transactionApi = {
    getAll: (params?: {
        accountId?: number; categoryId?: number; type?: TransactionType;
        dateFrom?: string; dateTo?: string; page?: number; size?: number;
    }) => {
        const q = new URLSearchParams();
        if (params?.accountId) q.set('accountId', String(params.accountId));
        if (params?.categoryId) q.set('categoryId', String(params.categoryId));
        if (params?.type) q.set('type', params.type);
        if (params?.dateFrom) q.set('dateFrom', params.dateFrom);
        if (params?.dateTo) q.set('dateTo', params.dateTo);
        if (params?.page !== undefined) q.set('page', String(params.page));
        if (params?.size !== undefined) q.set('size', String(params.size));
        return request<PagedResponse<Transaction>>(`/transactions?${q.toString()}`);
    },
    create: (data: CreateTransactionRequest) => request<Transaction>('/transactions', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: number, data: Partial<CreateTransactionRequest>) => request<Transaction>(`/transactions/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: number) => request<void>(`/transactions/${id}`, { method: 'DELETE' }),
};


// ─── Transfers ────────────────────────────────────────────────────────────────
export const transferApi = {
    getAll: (page = 0, size = 10) => request<PagedResponse<Transfer>>(`/transfers?page=${page}&size=${size}`),
    create: (data: CreateTransferRequest) => request<Transfer>('/transfers', { method: 'POST', body: JSON.stringify(data) }),
    delete: (id: number) => request<void>(`/transfers/${id}`, { method: 'DELETE' }),
};

// ─── Banks ──────────────────────────────────────────────────────────────────
// One set of endpoints for every provider; the provider is just a value.
export const bankApi = {
    providers: () => request<BankProvider[]>('/bank/providers'),
    connections: () => request<BankConnection[]>('/bank/connections'),
    // Credentials are verified against the bank before anything is stored.
    connect: (provider: string, credentials: Record<string, string>) =>
        request<BankConnection>('/bank/connections', {
            method: 'POST', body: JSON.stringify({ provider, credentials }),
        }),
    verify: (id: number) => request<BankConnection>(`/bank/connections/${id}/verify`, { method: 'POST' }),
    updateCredentials: (id: number, credentials: Record<string, string>) =>
        request<BankConnection>(`/bank/connections/${id}/credentials`, {
            method: 'PUT', body: JSON.stringify({ credentials }),
        }),
    disconnect: (id: number) => request<void>(`/bank/connections/${id}`, { method: 'DELETE' }),
    remoteAccounts: (id: number) => request<RemoteAccount[]>(`/bank/connections/${id}/accounts`),
    link: (id: number, accountId: number, externalAccountId: string) =>
        request<Account>(`/bank/connections/${id}/accounts/${accountId}/link`, {
            method: 'POST', body: JSON.stringify({ externalAccountId }),
        }),
    unlink: (accountId: number) => request<Account>(`/bank/accounts/${accountId}/link`, { method: 'DELETE' }),
    preview: (accountId: number, dateFrom: string, dateTo: string) =>
        request<PendingImport[]>('/bank/import/preview', {
            method: 'POST', body: JSON.stringify({ accountId, dateFrom, dateTo }),
        }),
    confirm: (accountId: number, dateFrom: string, dateTo: string, categories: Record<string, number | null>) =>
        request<ImportResult>('/bank/import/confirm', {
            method: 'POST', body: JSON.stringify({ accountId, dateFrom, dateTo, categories }),
        }),
};

// ─── CSV import ─────────────────────────────────────────────────────────────
export const csvImportApi = {
    /**
     * Uploads the file for parsing. Content-Type is deliberately not set: the browser
     * has to add it itself so the multipart boundary is included.
     */
    preview: async (file: File, accountId: number, mapping?: Partial<CsvMapping>): Promise<CsvPreview> => {
        const form = new FormData();
        form.append('file', file);
        form.append('accountId', String(accountId));
        Object.entries(mapping ?? {}).forEach(([k, v]) => {
            if (v) form.append(k, String(v));
        });

        const token = localStorage.getItem('token');
        const res = await fetch('/api/import/csv/preview', {
            method: 'POST',
            headers: token ? { Authorization: `Bearer ${token}` } : {},
            body: form,
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({ message: res.statusText }));
            throw new Error(err.message || res.statusText);
        }
        return res.json();
    },

    confirm: (accountId: number, rows: CsvRow[], categories: Record<string, number | null>) =>
        request<ImportResult>('/import/csv/confirm', {
            method: 'POST', body: JSON.stringify({ accountId, rows, categories }),
        }),
};
