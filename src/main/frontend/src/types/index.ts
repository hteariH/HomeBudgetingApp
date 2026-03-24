// ─── Domain Types ─────────────────────────────────────────────────────────────

export interface Currency {
    id: number;
    code: string;
    name: string;
    symbol: string;
    exchangeRateToBase: number;
    isBase: boolean;
    createdAt: string;
}

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CASH' | 'CREDIT' | 'INVESTMENT';

export interface Account {
    id: number;
    name: string;
    type: AccountType;
    balance: number;
    currency: Currency;
    description?: string;
    color: string;
    monobankAccountId?: string;
    isMonobankLinked: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface MonobankAccountInfo {
    id: string;
    currencyCode: number;
    balance: number;
    creditLimit: number;
    type: string;
    maskedPan: string[];
}

export interface MonobankClientInfo {
    clientId: string;
    name: string;
    accounts: MonobankAccountInfo[];
}

export interface MonobankImportResult {
    imported: number;
    skipped: number;
}

export interface MonobankPendingTransaction {
    monobankId: string;
    date: string;
    description: string;
    amount: number;
    type: TransactionType;
    mcc: number;
    alreadyExists: boolean;
    existingTransactionId?: number;
    suggestedCategoryId?: number;
}

export interface MonobankPreviewResponse {
    transactions: MonobankPendingTransaction[];
}

export interface MonobankConfirmItem {
    monobankId: string;
    categoryId: number;
}

export type CategoryType = 'INCOME' | 'EXPENSE';

export interface Category {
    id: number;
    name: string;
    type: CategoryType;
    icon: string;
    color: string;
    description?: string;
    mccCodes: number[];
    createdAt: string;
}

export type TransactionType = 'INCOME' | 'EXPENSE';

export interface Transaction {
    id: number;
    account: Account;
    category: Category;
    amount: number;
    type: TransactionType;
    date: string;
    description?: string;
    createdAt: string;
    updatedAt: string;
}

export interface PagedResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

// ─── Request Types ─────────────────────────────────────────────────────────────

export interface CreateAccountRequest {
    name: string;
    type: AccountType;
    initialBalance: number;
    currencyId: number;
    description?: string;
    color: string;
}

export interface UpdateAccountRequest {
    name?: string;
    type?: AccountType;
    description?: string;
    color?: string;
}

export interface CreateCategoryRequest {
    name: string;
    type: CategoryType;
    icon: string;
    color: string;
    description?: string;
}

export interface UpdateCategoryRequest {
    name?: string;
    icon?: string;
    color?: string;
    description?: string;
}

export interface CreateTransactionRequest {
    accountId: number;
    categoryId: number;
    amount: number;
    type: TransactionType;
    date: string;
    description?: string;
}

export interface CreateCurrencyRequest {
    code: string;
    name: string;
    symbol: string;
    exchangeRateToBase: number;
    isBase: boolean;
}

export interface Transfer {
    id: number;
    fromAccount: Account;
    toAccount: Account;
    fromAmount: number;
    toAmount: number;
    exchangeRate: number;
    date: string;
    description?: string;
    createdAt: string;
}

export interface CreateTransferRequest {
    fromAccountId: number;
    toAccountId: number;
    fromAmount: number;
    toAmount: number;
    date: string;
    description?: string;
}

export interface UpdateCurrencyRequest {
    name?: string;
    symbol?: string;
    exchangeRateToBase?: number;
}
