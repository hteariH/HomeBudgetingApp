import { api } from './http';
import type * as T from './types';

/**
 * One function per endpoint, named after RestApi.kt / HomeBudgetApi.kt so the Compose
 * view models read straight across when porting a screen.
 */
export const endpoints = {
    // ── accounts ────────────────────────────────────────────────────────────
    accounts: () => api<T.RestAccount[]>('/accounts'),
    account: (id: number) => api<T.RestAccount>(`/accounts/${id}`),
    createAccount: (body: T.CreateAccountRequest) => api<T.RestAccount>('/accounts', { method: 'POST', json: body }),
    updateAccount: (id: number, body: T.UpdateAccountRequest) =>
        api<T.RestAccount>(`/accounts/${id}`, { method: 'PUT', json: body }),
    deleteAccount: (id: number) => api<void>(`/accounts/${id}`, { method: 'DELETE' }),

    // ── categories ──────────────────────────────────────────────────────────
    categories: (type?: T.CategoryType) => api<T.RestCategory[]>('/categories', { query: { type } }),
    createCategory: (body: T.CreateCategoryRequest) =>
        api<T.RestCategory>('/categories', { method: 'POST', json: body }),
    updateCategory: (id: number, body: T.CreateCategoryRequest) =>
        api<T.RestCategory>(`/categories/${id}`, { method: 'PUT', json: body }),
    deleteCategory: (id: number) => api<void>(`/categories/${id}`, { method: 'DELETE' }),

    // ── transactions ────────────────────────────────────────────────────────
    transactions: (q: {
        accountId?: number;
        categoryId?: number;
        type?: T.TransactionType;
        dateFrom?: string;
        dateTo?: string;
        page?: number;
        size?: number;
    } = {}) => api<T.Paged<T.RestTransaction>>('/transactions', { query: { page: 0, size: 50, ...q } }),
    transaction: (id: number) => api<T.RestTransaction>(`/transactions/${id}`),
    createTransaction: (body: T.CreateTransactionRequest) =>
        api<T.RestTransaction>('/transactions', { method: 'POST', json: body }),
    updateTransaction: (id: number, body: T.UpdateTransactionRequest) =>
        api<T.RestTransaction>(`/transactions/${id}`, { method: 'PUT', json: body }),
    deleteTransaction: (id: number) => api<void>(`/transactions/${id}`, { method: 'DELETE' }),

    // ── transfers ───────────────────────────────────────────────────────────
    transfers: (page = 0, size = 20) => api<T.Paged<T.RestTransfer>>('/transfers', { query: { page, size } }),
    createTransfer: (body: T.CreateTransferRequest) =>
        api<T.RestTransfer>('/transfers', { method: 'POST', json: body }),
    deleteTransfer: (id: number) => api<void>(`/transfers/${id}`, { method: 'DELETE' }),

    // ── subscriptions ───────────────────────────────────────────────────────
    subscriptions: () => api<T.RestSubscription[]>('/subscriptions'),
    subscription: (id: number) => api<T.RestSubscription>(`/subscriptions/${id}`),
    createSubscription: (body: T.CreateSubscriptionRequest) =>
        api<T.RestSubscription>('/subscriptions', { method: 'POST', json: body }),
    updateSubscription: (id: number, body: T.UpdateSubscriptionRequest) =>
        api<T.RestSubscription>(`/subscriptions/${id}`, { method: 'PUT', json: body }),
    deleteSubscription: (id: number) => api<void>(`/subscriptions/${id}`, { method: 'DELETE' }),

    // ── banks ───────────────────────────────────────────────────────────────
    bankProviders: () => api<T.RestBankProvider[]>('/bank/providers'),
    bankConnections: () => api<T.RestBankConnection[]>('/bank/connections'),
    connectBank: (body: T.CreateConnectionRequest) =>
        api<T.RestBankConnection>('/bank/connections', { method: 'POST', json: body }),
    beginBankConsent: (providerId: string) =>
        api<T.RestConsentChallenge>(`/bank/providers/${providerId}/consent`, { method: 'POST' }),
    bankConsentStatus: (providerId: string, reference: string) =>
        api<T.RestConsentStatus>(`/bank/providers/${providerId}/consent/${reference}`, { method: 'POST' }),
    verifyBank: (id: number) => api<T.RestBankConnection>(`/bank/connections/${id}/verify`, { method: 'POST' }),
    updateBankCredentials: (id: number, body: T.UpdateCredentialsRequest) =>
        api<T.RestBankConnection>(`/bank/connections/${id}/credentials`, { method: 'PUT', json: body }),
    disconnectBank: (id: number) => api<void>(`/bank/connections/${id}`, { method: 'DELETE' }),
    remoteAccounts: (connectionId: number) =>
        api<T.RestRemoteAccount[]>(`/bank/connections/${connectionId}/accounts`),
    linkAccount: (connectionId: number, accountId: number, body: T.LinkAccountRequest) =>
        api<void>(`/bank/connections/${connectionId}/accounts/${accountId}/link`, { method: 'POST', json: body }),
    unlinkAccount: (accountId: number) => api<void>(`/bank/accounts/${accountId}/link`, { method: 'DELETE' }),
    previewBankImport: (body: T.ImportPreviewRequest) =>
        api<T.RestPendingImport[]>('/bank/import/preview', { method: 'POST', json: body }),
    confirmBankImport: (body: T.ImportConfirmRequest) =>
        api<T.RestImportResult>('/bank/import/confirm', { method: 'POST', json: body }),

    // ── csv import ──────────────────────────────────────────────────────────
    previewCsv: (accountId: number, file: File, mapping?: T.RestCsvMapping | null) => {
        const form = new FormData();
        // The server reads the part by name, but a filename is what makes Spring treat it
        // as an uploaded file at all.
        form.append('file', file, file.name);
        return api<T.RestCsvPreview>('/import/csv/preview', {
            method: 'POST',
            form,
            query: {
                accountId,
                date: mapping?.date,
                description: mapping?.description,
                amount: mapping?.amount,
                debit: mapping?.debit,
                credit: mapping?.credit,
                currency: mapping?.currency,
                dateFormat: mapping?.dateFormat,
            },
        });
    },
    confirmCsv: (body: T.CsvConfirmRequest) =>
        api<T.RestImportResult>('/import/csv/confirm', { method: 'POST', json: body }),

    // ── homemoney import ────────────────────────────────────────────────────
    homeMoneyConnection: () => api<T.RestHomeMoneyConnection>('/import/homemoney/connection'),
    authorizeHomeMoney: () => api<T.RestHomeMoneyAuthorization>('/import/homemoney/authorize', { method: 'POST' }),
    connectHomeMoneyWithCode: (code: string, state: string) =>
        api<T.RestHomeMoneyConnection>('/import/homemoney/connection/code', { method: 'POST', json: { code, state } }),
    disconnectHomeMoney: () => api<void>('/import/homemoney/connection', { method: 'DELETE' }),
    homeMoneyPlan: () => api<T.RestHomeMoneyPlan>('/import/homemoney/plan', { method: 'POST' }),
    applyHomeMoney: (body: T.HomeMoneyApplyRequest) =>
        api<T.RestHomeMoneyStatus>('/import/homemoney/apply', { method: 'POST', json: body }),
    homeMoneyStatus: () => api<T.RestHomeMoneyStatus>('/import/homemoney/status'),

    // ── fx ──────────────────────────────────────────────────────────────────
    fxRates: () => api<T.RestFxRate[]>('/fx/rates'),
    setFxOverride: (code: string, perUsd: string) =>
        api<void>(`/fx/rates/${code}`, { method: 'PUT', json: { rate: perUsd } }),
    clearFxOverride: (code: string) => api<void>(`/fx/rates/${code}`, { method: 'DELETE' }),

    // ── currencies and the user ─────────────────────────────────────────────
    currencies: () => api<T.RestCurrency[]>('/currencies'),
    setBaseCurrency: (currencyId: number) =>
        api<void>('/users/me/base-currency', { method: 'PUT', query: { currencyId } }),
    me: () => api<T.UserDto>('/users/me'),
    deleteMe: () => api<void>('/users/me', { method: 'DELETE' }),
    logout: () => api<void>('/auth/logout', { method: 'POST' }),
    billingStatus: () => api<T.BillingStatusDto>('/billing/status'),
};
