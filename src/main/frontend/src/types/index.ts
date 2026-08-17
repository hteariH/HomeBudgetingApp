// NOTE: money fields are decimal **strings**, not numbers.
// The server serialises every BigDecimal as a string so amounts never pass through a
// double, and so trailing zeros survive (which is how a 0-decimal currency like JPY is
// distinguishable from a 2-decimal one). Coerce with Number() before arithmetic.

// ─── Domain Types ─────────────────────────────────────────────────────────────

export interface Currency {
    id: number;
    code: string;
    name: string;
    symbol: string;
    /** Digits after the decimal point. Format against this - not every currency has 2. */
    decimals: number;
    sortOrder: number;
    exchangeRateToBase: string;
    isBase: boolean;
    createdAt: string;
}

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CASH' | 'CREDIT' | 'INVESTMENT';

export interface Account {
    id: number;
    /** Sync identity, shared with the mobile client. Server ids are not portable. */
    uuid: string;
    name: string;
    type: AccountType;
    /** Current balance, summed from the ledger by the server. Not a stored column. */
    balance: string;
    /** Opening balance - this is the editable one. */
    initialBalance: string;
    currency: Currency;
    description?: string;
    icon: string;
    color: string;
    archived: boolean;
    sortOrder: number;
    includeInTotal: boolean;
    bankConnectionId?: number | null;
    externalAccountId?: string | null;
    createdAt: string;
    updatedAt: string;
}

export type CategoryType = 'INCOME' | 'EXPENSE';

export interface Category {
    id: number;
    uuid: string;
    name: string;
    type: CategoryType;
    icon: string;
    color: string;
    description?: string;
    parentId?: number | null;
    archived: boolean;
    sortOrder: number;
    isDefault: boolean;
    /** Monthly limit in the user's base currency, or null for no budget. */
    budgetLimit?: string | null;
    mccCodes: number[];
    createdAt: string;
}

// TRANSFER rows are the two legs of a transfer; they carry no category.
export type TransactionType = 'INCOME' | 'EXPENSE' | 'TRANSFER';

export interface Transaction {
    id: number;
    uuid: string;
    account: Account;
    /** Null for transfer legs, and for history whose category was deleted. */
    category: Category | null;
    amount: string;
    currencyCode: string;
    accountAmount: string;
    type: TransactionType;
    /** Derived from occurredAt; kept for date-only views. */
    date: string;
    occurredAt: string;
    description?: string;
    transferGroupId?: string | null;
    subscriptionId?: number | null;
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
    transferGroupId: string;
    fromAccount: Account;
    toAccount: Account;
    fromAmount: string;
    toAmount: string;
    fee?: string | null;
    exchangeRate: string;
    date: string;
    occurredAt: string;
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


// ─── Bank integrations ──────────────────────────────────────────────────────
// Nothing here names a specific bank. A provider describes its own connect form, so
// adding one is a server-side change only.

export type CredentialFieldType = 'SECRET' | 'TEXT';

export interface CredentialField {
    name: string;
    label: string;
    type: CredentialFieldType;
    placeholder?: string | null;
    helpText?: string | null;
    required: boolean;
}

export interface BankProvider {
    id: string;
    displayName: string;
    helpUrl?: string | null;
    credentialFields: CredentialField[];
}

export type BankConnectionStatus = 'ACTIVE' | 'INVALID';

export interface BankConnection {
    id: number;
    provider: string;
    providerDisplayName: string;
    label?: string | null;
    status: BankConnectionStatus;
    lastVerifiedAt?: string | null;
    linkedAccountIds: number[];
    createdAt: string;
}

export interface RemoteAccount {
    externalId: string;
    displayName: string;
    currencyCode: string;
    balanceMinor: number;
    type?: string | null;
    linkedAccountId?: number | null;
}

export interface PendingImport {
    externalId: string;
    occurredAt: string;
    date: string;
    description: string;
    amountMinor: number;
    type: TransactionType;
    mcc: number;
    alreadyImported: boolean;
    suggestedCategoryId?: number | null;
}

export interface ImportResult {
    imported: number;
    skipped: number;
}

// ─── CSV statement import ───────────────────────────────────────────────────

export interface CsvMapping {
    date: string;
    description: string;
    amount?: string | null;
    debit?: string | null;
    credit?: string | null;
    currency?: string | null;
    externalId?: string | null;
    dateFormat?: string | null;
}

export interface CsvRow {
    externalId: string;
    occurredAt: string;
    date: string;
    description: string;
    /** Hundredths, as the file reported them. */
    amountMinor: number;
    currencyCode?: string | null;
    type: TransactionType;
    alreadyImported: boolean;
    suggestedCategoryId?: number | null;
}

export interface CsvPreview {
    headers: string[];
    /** What the server detected. Send it back corrected if it guessed wrong. */
    mapping: CsvMapping;
    rows: CsvRow[];
    /** Lines that could not be read - shown rather than silently dropped. */
    skipped: string[];
    /** Currencies in the file that differ from the target account's. */
    currencyMismatch: string[];
}
