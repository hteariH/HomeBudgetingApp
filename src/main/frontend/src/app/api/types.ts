/**
 * The REST contract, transcribed from the Kotlin DTOs in
 * client/composeApp/src/commonMain/kotlin/com/personalmoney/core/network.
 *
 * Every amount is a string: the server serialises BigDecimal as a string so no amount ever
 * passes through a double. Parse with `parseAmount`, never `Number`.
 */

export type TransactionType = 'EXPENSE' | 'INCOME' | 'TRANSFER';
export type CategoryType = 'EXPENSE' | 'INCOME';
export type SubscriptionFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export interface Paged<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

// ── RestDtos.kt ─────────────────────────────────────────────────────────────

export interface RestCurrency {
    id: number;
    code: string;
    name: string;
    symbol: string;
    decimals: number;
    sortOrder?: number;
    exchangeRateToBase?: string | null;
    isBase?: boolean;
}

export interface RestAccount {
    id: number;
    uuid: string;
    name: string;
    type: string;
    balance: string;
    initialBalance: string;
    currency: RestCurrency;
    description?: string | null;
    icon?: string;
    color?: string;
    archived?: boolean;
    sortOrder?: number;
    includeInTotal?: boolean;
    bankConnectionId?: number | null;
    externalAccountId?: string | null;
}

export interface RestCategory {
    id: number;
    uuid: string;
    name: string;
    type: CategoryType;
    icon: string;
    color: string;
    description?: string | null;
    parentId?: number | null;
    archived?: boolean;
    sortOrder?: number;
    isDefault?: boolean;
    budgetLimit?: string | null;
    mccCodes?: number[];
}

export interface RestTransaction {
    id: number;
    uuid: string;
    account: RestAccount;
    category?: RestCategory | null;
    amount: string;
    currencyCode: string;
    accountAmount: string;
    type: TransactionType;
    date: string;
    occurredAt: string;
    description?: string | null;
    transferGroupId?: string | null;
    subscriptionId?: number | null;
}

export interface RestSubscription {
    id: number;
    uuid: string;
    name: string;
    account: RestAccount;
    category?: RestCategory | null;
    amount: string;
    currencyCode: string;
    frequency: SubscriptionFrequency;
    intervalCount?: number;
    nextChargeDate: string;
    startDate: string;
    endDate?: string | null;
    active?: boolean;
    reminderMinutesBefore?: number | null;
    icon?: string;
    color?: string;
    note?: string | null;
}

export interface RestFxRate {
    baseCode: string;
    quoteCode: string;
    rate: string;
    rateDate: string;
    source: string;
}

export interface RestTransfer {
    id: number;
    transferGroupId: string;
    fromAccount: RestAccount;
    toAccount: RestAccount;
    fromAmount: string;
    toAmount: string;
    fee?: string | null;
    exchangeRate: string;
    date: string;
    occurredAt: string;
    description?: string | null;
}

export interface CreateAccountRequest {
    name: string;
    type?: string;
    initialBalance?: string;
    currencyId: number;
    description?: string | null;
    icon?: string;
    color?: string;
    sortOrder?: number;
    includeInTotal?: boolean;
}

export interface UpdateAccountRequest {
    name?: string | null;
    type?: string | null;
    initialBalance?: string | null;
    description?: string | null;
    icon?: string | null;
    color?: string | null;
    archived?: boolean | null;
    sortOrder?: number | null;
    includeInTotal?: boolean | null;
}

export interface CreateCategoryRequest {
    name: string;
    type: CategoryType;
    icon?: string;
    color?: string;
    description?: string | null;
    parentId?: number | null;
    sortOrder?: number;
    budgetLimit?: string | null;
}

export interface CreateTransactionRequest {
    accountId: number;
    categoryId?: number | null;
    amount: string;
    type: TransactionType;
    date: string;
    /** Preferred over [date] by the server when present: the time of day, not just the day. */
    occurredAt?: string | null;
    description?: string | null;
}

/**
 * What the server lets an edit change. Account and type are fixed once a row exists, and
 * a transfer leg cannot be edited at all - the server answers that with a 400.
 */
export interface UpdateTransactionRequest {
    categoryId?: number | null;
    amount?: string | null;
    date?: string | null;
    occurredAt?: string | null;
    description?: string | null;
}

export interface CreateTransferRequest {
    fromAccountId: number;
    toAccountId: number;
    fromAmount: string;
    toAmount: string;
    fee?: string | null;
    date: string;
    occurredAt?: string | null;
    description?: string | null;
}

export interface CreateSubscriptionRequest {
    name: string;
    accountId: number;
    categoryId?: number | null;
    amount: string;
    frequency: SubscriptionFrequency;
    intervalCount?: number;
    startDate: string;
    nextChargeDate?: string | null;
    endDate?: string | null;
    reminderMinutesBefore?: number | null;
    icon?: string;
    color?: string;
    note?: string | null;
}

/** Every field optional: the server patches only what is present. */
export interface UpdateSubscriptionRequest {
    name?: string | null;
    categoryId?: number | null;
    amount?: string | null;
    frequency?: SubscriptionFrequency | null;
    intervalCount?: number | null;
    nextChargeDate?: string | null;
    endDate?: string | null;
    active?: boolean | null;
    reminderMinutesBefore?: number | null;
    icon?: string | null;
    color?: string | null;
    note?: string | null;
}

// ── BankDtos.kt ─────────────────────────────────────────────────────────────

export interface RestCredentialField {
    name: string;
    label: string;
    type: string;
    placeholder?: string | null;
    helpText?: string | null;
    required?: boolean;
}

export interface RestBankProvider {
    id: string;
    displayName: string;
    helpUrl?: string | null;
    authMode?: string;
    credentialFields?: RestCredentialField[];
}

export interface RestConsentChallenge {
    provider: string;
    reference: string;
    approveUrl: string;
    expiresAt: string;
    qrRows?: string[];
}

export interface RestConsentStatus {
    status: string;
    connection?: RestBankConnection | null;
}

export interface RestBankConnection {
    id: number;
    provider: string;
    providerDisplayName: string;
    label?: string | null;
    status: string;
    lastVerifiedAt?: string | null;
    linkedAccountIds?: number[];
}

export interface RestRemoteAccount {
    externalId: string;
    displayName: string;
    currencyCode: string;
    balanceMinor: number;
    type?: string | null;
    linkedAccountId?: number | null;
}

export interface RestPendingImport {
    externalId: string;
    occurredAt: string;
    date: string;
    description: string;
    amountMinor: number;
    type: TransactionType;
    mcc?: number;
    alreadyImported?: boolean;
    suggestedCategoryId?: number | null;
}

export interface RestImportResult {
    imported: number;
    skipped: number;
}

export interface CreateConnectionRequest {
    provider: string;
    credentials?: Record<string, string>;
}

export interface UpdateCredentialsRequest {
    credentials?: Record<string, string>;
}

export interface LinkAccountRequest {
    externalAccountId: string;
}

export interface ImportPreviewRequest {
    accountId: number;
    dateFrom: string;
    dateTo: string;
}

export interface ImportConfirmRequest {
    accountId: number;
    dateFrom: string;
    dateTo: string;
    categories?: Record<string, number | null>;
    transfers?: Record<string, number | null>;
}

// ── CsvDtos.kt ──────────────────────────────────────────────────────────────

export interface RestCsvMapping {
    date: string;
    description: string;
    amount?: string | null;
    debit?: string | null;
    credit?: string | null;
    currency?: string | null;
    externalId?: string | null;
    dateFormat?: string | null;
}

export interface RestCsvRow {
    externalId: string;
    occurredAt: string;
    date: string;
    description: string;
    amountMinor: number;
    currencyCode?: string | null;
    type: TransactionType;
    alreadyImported?: boolean;
    suggestedCategoryId?: number | null;
}

export interface RestCsvPreview {
    headers?: string[];
    mapping: RestCsvMapping;
    rows?: RestCsvRow[];
    skipped?: string[];
    currencyMismatch?: string[];
}

export interface CsvConfirmRequest {
    accountId: number;
    rows?: RestCsvRow[];
    categories?: Record<string, number | null>;
    transfers?: Record<string, number | null>;
}

// ── HomeMoneyDtos.kt ────────────────────────────────────────────────────────

export interface RestHomeMoneyConnection {
    connected?: boolean;
    label?: string | null;
    status?: string | null;
    lastVerifiedAt?: string | null;
}

export interface RestHomeMoneyAuthorization {
    authorizationUrl: string;
    state?: string;
}

export interface RestPlannedAccount {
    externalId: string;
    name: string;
    currencyLabel: string;
    currencyCode?: string | null;
    balance: string;
    targetAccountId?: number | null;
    included: boolean;
}

export interface RestPlannedCategory {
    externalId: string;
    name: string;
    fullName: string;
    type: CategoryType;
    parentExternalId?: string | null;
    archived: boolean;
    targetCategoryId?: number | null;
}

export interface RestHomeMoneyPlan {
    accounts?: RestPlannedAccount[];
    categories?: RestPlannedCategory[];
    transactionCount?: number;
    firstDate?: string | null;
    lastDate?: string | null;
    newAccountCount?: number;
    newCategoryCount?: number;
    blockedReason?: string | null;
}

export interface HomeMoneyApplyRequest {
    accounts: RestPlannedAccount[];
    categories: RestPlannedCategory[];
}

export interface RestHomeMoneyStatus {
    state?: string;
    accountsCreated?: number;
    categoriesCreated?: number;
    imported?: number;
    skipped?: number;
    processedChunks?: number;
    totalChunks?: number;
    error?: string | null;
}

// ── ApiDtos.kt ──────────────────────────────────────────────────────────────

export interface CurrencyDto {
    id: number;
    code: string;
    name: string;
    symbol: string;
    decimals?: number;
    sortOrder?: number;
}

export interface UserDto {
    id: number;
    email: string;
    firstName?: string;
    lastName?: string;
    baseCurrency?: CurrencyDto | null;
    role?: 'USER' | 'ADMIN';
}

export interface JwtResponseDto {
    token: string;
    refreshToken: string;
    email: string;
    firstName?: string;
    lastName?: string;
    role?: string;
}

export interface InstanceDto {
    name: string;
    version: string;
    apiVersion: number;
    setupRequired: boolean;
    registrationOpen: boolean;
    mailEnabled: boolean;
    bankProviders: string[];
}

export interface AdminUserDto {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    role: 'USER' | 'ADMIN';
    createdAt: string;
    lastSignInAt?: string | null;
    householdId?: number | null;
}

export interface RegistrationSettingDto {
    open: boolean;
    lockedByEnvironment: boolean;
}

export interface ResetLinkDto {
    path: string;
    expiresAt: string;
}
