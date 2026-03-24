import {
    Account, Category, CategoryType, CreateAccountRequest, CreateCategoryRequest,
    CreateCurrencyRequest, CreateTransactionRequest, CreateTransferRequest,
    Currency, MonobankClientInfo, MonobankConfirmItem, MonobankImportResult,
    MonobankPreviewResponse, PagedResponse, Transaction, Transfer,
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
        accountId?: number; categoryId?: number; type?: 'INCOME' | 'EXPENSE';
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

// ─── Monobank ──────────────────────────────────────────────────────
const monobankBase = '/monobank';
export const monobankApi = {
    getClientInfo: (token: string) =>
        request<MonobankClientInfo>(`${monobankBase}/client-info?token=${encodeURIComponent(token)}`),
    linkAccount: (accountId: number, token: string, monobankAccountId: string) =>
        request<void>(`${monobankBase}/accounts/${accountId}/link`, { method: 'POST', body: JSON.stringify({ token, monobankAccountId }) }),
    unlinkAccount: (accountId: number) =>
        request<void>(`${monobankBase}/accounts/${accountId}/link`, { method: 'DELETE' }),
    preview: (accountId: number, dateFrom: number, dateTo: number) =>
        request<MonobankPreviewResponse>(`${monobankBase}/preview`, { method: 'POST', body: JSON.stringify({ accountId, dateFrom, dateTo }) }),
    confirmImport: (accountId: number, dateFrom: number, dateTo: number, items: MonobankConfirmItem[]) =>
        request<MonobankImportResult>(`${monobankBase}/confirm`, { method: 'POST', body: JSON.stringify({ accountId, dateFrom, dateTo, items }) }),
};

// ─── Transfers ────────────────────────────────────────────────────────────────
export const transferApi = {
    getAll: (page = 0, size = 10) => request<PagedResponse<Transfer>>(`/transfers?page=${page}&size=${size}`),
    create: (data: CreateTransferRequest) => request<Transfer>('/transfers', { method: 'POST', body: JSON.stringify(data) }),
    delete: (id: number) => request<void>(`/transfers/${id}`, { method: 'DELETE' }),
};
