package com.homebudget.app.dto

import com.homebudget.app.domain.*
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

// ───────── Currency DTOs ─────────

data class CurrencyResponse(
    val id: Long,
    val code: String,
    val name: String,
    val symbol: String,
    /** Digits after the decimal point. Clients must format against this, not assume 2. */
    val decimals: Int,
    val sortOrder: Int,
    val exchangeRateToBase: BigDecimal,
    val isBase: Boolean,
    val createdAt: LocalDateTime
)

data class CreateCurrencyRequest(
    @field:NotBlank @field:Size(min = 3, max = 10) val code: String,
    @field:NotBlank val name: String,
    @field:NotBlank @field:Size(max = 10) val symbol: String,
    @field:Min(0) @field:Max(18) val decimals: Int = 2,
    @field:Positive val exchangeRateToBase: BigDecimal = BigDecimal.ONE,
    val isBase: Boolean = false
)

data class UpdateCurrencyRequest(
    val name: String? = null,
    val symbol: String? = null,
    @field:Min(0) @field:Max(18) val decimals: Int? = null,
    val exchangeRateToBase: BigDecimal? = null
)

// ───────── Account DTOs ─────────

data class AccountResponse(
    val id: Long,
    /** Sync identity. Stable across clients; server ids are not. */
    val uuid: String,
    val name: String,
    val type: AccountType,
    /** Current balance, summed from the ledger. Not a stored column. */
    val balance: BigDecimal,
    /** Opening balance, which is what [UpdateAccountRequest.initialBalance] edits. */
    val initialBalance: BigDecimal,
    val currency: CurrencyResponse,
    val description: String?,
    val icon: String,
    val color: String,
    val archived: Boolean,
    val sortOrder: Int,
    val includeInTotal: Boolean,
    /** Set when a bank connection feeds this account. */
    val bankConnectionId: Long?,
    val externalAccountId: String?,
    val createdAt: LocalDateTime,
    val updatedAt: Instant
)

data class CreateAccountRequest(
    @field:NotBlank val name: String,
    val type: AccountType = AccountType.CHECKING,
    val initialBalance: BigDecimal = BigDecimal.ZERO,
    @field:Positive val currencyId: Long,
    val description: String? = null,
    val icon: String = "wallet",
    val color: String = "#4F46E5",
    val sortOrder: Int = 0,
    val includeInTotal: Boolean = true
)

data class UpdateAccountRequest(
    val name: String? = null,
    val type: AccountType? = null,
    val initialBalance: BigDecimal? = null,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean? = null,
    val sortOrder: Int? = null,
    val includeInTotal: Boolean? = null
)

// ───────── Category DTOs ─────────

data class CategoryResponse(
    val id: Long,
    val uuid: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: String,
    val description: String?,
    val parentId: Long?,
    val archived: Boolean,
    val sortOrder: Int,
    val isDefault: Boolean,
    /** Monthly limit in the user's base currency, or null for no budget. */
    val budgetLimit: BigDecimal?,
    val mccCodes: Set<Int>,
    val createdAt: LocalDateTime
)

data class CreateCategoryRequest(
    @field:NotBlank val name: String,
    val type: CategoryType,
    val icon: String = "tag",
    val color: String = "#6366F1",
    val description: String? = null,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    @field:PositiveOrZero val budgetLimit: BigDecimal? = null
)

data class UpdateCategoryRequest(
    val name: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val description: String? = null,
    val parentId: Long? = null,
    val archived: Boolean? = null,
    val sortOrder: Int? = null,
    @field:PositiveOrZero val budgetLimit: BigDecimal? = null,
    /** Explicit flag, because a null budgetLimit means "unchanged" rather than "clear". */
    val clearBudgetLimit: Boolean = false,
    val mccCodes: Set<Int>? = null
)

// ───────── Transaction DTOs ─────────

data class TransactionResponse(
    val id: Long,
    val uuid: String,
    val account: AccountResponse,
    val category: CategoryResponse?,
    val amount: BigDecimal,
    val currencyCode: String,
    /** The amount converted into the account's currency; differs only for cross-currency entries. */
    val accountAmount: BigDecimal,
    val type: TransactionType,
    /** Kept for clients that only deal in dates; derived from [occurredAt]. */
    val date: LocalDate,
    val occurredAt: Instant,
    val description: String?,
    val transferGroupId: String?,
    val subscriptionId: Long?,
    /** Null for entries no person wrote, such as subscription charges. */
    val createdByUserId: Long?,
    val createdAt: LocalDateTime,
    val updatedAt: Instant,
    /** The receipt this entry was saved from, if any. */
    val receiptUuid: String? = null,
    /** `APP`, or `MCP` for an entry an assistant recorded. */
    val source: String = "APP"
)

data class CreateTransactionRequest(
    @field:Positive val accountId: Long,
    /** Optional: an uncategorised entry is better than a wrong one. */
    val categoryId: Long? = null,
    @field:Positive val amount: BigDecimal,
    val type: TransactionType,
    val date: LocalDate,
    /** Preferred over [date] when the client knows the time of day. */
    val occurredAt: Instant? = null,
    val description: String? = null,
    /** A receipt of the household's from `POST /api/receipts`; it is kept with this entry. */
    val receiptUuid: String? = null
)

data class UpdateTransactionRequest(
    val categoryId: Long? = null,
    val amount: BigDecimal? = null,
    val date: LocalDate? = null,
    val occurredAt: Instant? = null,
    val description: String? = null,
    /** Attaches a receipt; null leaves the current one. */
    val receiptUuid: String? = null
)

// ───────── Transfer DTOs ─────────

data class TransferResponse(
    val id: Long,
    val transferGroupId: String,
    val fromAccount: AccountResponse,
    val toAccount: AccountResponse,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val fee: BigDecimal?,
    val exchangeRate: BigDecimal,
    val date: LocalDate,
    val occurredAt: Instant,
    val description: String?,
    val createdAt: LocalDateTime
)

data class CreateTransferRequest(
    @field:Positive val fromAccountId: Long,
    @field:Positive val toAccountId: Long,
    @field:Positive val fromAmount: BigDecimal,
    @field:Positive val toAmount: BigDecimal,
    @field:PositiveOrZero val fee: BigDecimal? = null,
    val date: LocalDate,
    val occurredAt: Instant? = null,
    val description: String? = null
)

// ───────── Subscription DTOs ─────────

data class SubscriptionResponse(
    val id: Long,
    val uuid: String,
    val name: String,
    val account: AccountResponse,
    val category: CategoryResponse?,
    val amount: BigDecimal,
    val currencyCode: String,
    val frequency: SubscriptionFrequency,
    val intervalCount: Int,
    val nextChargeDate: LocalDate,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val active: Boolean,
    val reminderMinutesBefore: Int?,
    val icon: String,
    val color: String,
    val note: String?,
    val createdAt: LocalDateTime,
    val updatedAt: Instant
)

data class CreateSubscriptionRequest(
    @field:NotBlank val name: String,
    @field:Positive val accountId: Long,
    val categoryId: Long? = null,
    @field:Positive val amount: BigDecimal,
    val frequency: SubscriptionFrequency,
    @field:Positive val intervalCount: Int = 1,
    val startDate: LocalDate,
    val nextChargeDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val reminderMinutesBefore: Int? = 24 * 60,
    val icon: String = "subscriptions",
    val color: String = "#008080",
    val note: String? = null
)

data class UpdateSubscriptionRequest(
    val name: String? = null,
    val categoryId: Long? = null,
    val amount: BigDecimal? = null,
    val frequency: SubscriptionFrequency? = null,
    @field:Positive val intervalCount: Int? = null,
    val nextChargeDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val active: Boolean? = null,
    val reminderMinutesBefore: Int? = null,
    val icon: String? = null,
    val color: String? = null,
    val note: String? = null
)

// ───────── Paged Response ─────────

data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

// ───────── Auth DTOs ─────────

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String
)

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    // 6 was too short for a site anyone can register on; short passwords are what
    // makes an online guessing budget worth spending.
    @field:NotBlank @field:Size(min = 10, max = 128) val password: String,
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    val baseCurrencyId: Long? = null,
    /** Joins the invitation's household instead of creating one; admits even when registration is closed. */
    val inviteToken: String? = null
)

data class JwtResponse(
    val token: String,
    /** Redeem at /api/auth/refresh once [token] expires. Rotated on every use. */
    val refreshToken: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String
)

data class ForgotPasswordRequest(
    @field:Email @field:NotBlank val email: String
)

data class ResetPasswordRequest(
    @field:NotBlank val token: String,
    @field:NotBlank @field:Size(min = 10, max = 128) val newPassword: String
)

data class VerifyEmailRequest(
    @field:NotBlank val token: String
)

data class UserResponse(
    val id: Long,
    val uuid: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: Role,
    val emailVerified: Boolean,
    val baseCurrency: CurrencyResponse?,
    val householdId: Long,
    val householdName: String,
    val householdRole: HouseholdRole
)
