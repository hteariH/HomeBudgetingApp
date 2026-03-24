package com.homebudget.app.dto

import com.homebudget.app.domain.*
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// ───────── Currency DTOs ─────────

data class CurrencyResponse(
    val id: Long,
    val code: String,
    val name: String,
    val symbol: String,
    val exchangeRateToBase: BigDecimal,
    val isBase: Boolean,
    val createdAt: LocalDateTime
)

data class CreateCurrencyRequest(
    @field:NotBlank @field:Size(min = 3, max = 3) val code: String,
    @field:NotBlank val name: String,
    @field:NotBlank @field:Size(max = 10) val symbol: String,
    @field:Positive val exchangeRateToBase: BigDecimal = BigDecimal.ONE,
    val isBase: Boolean = false
)

data class UpdateCurrencyRequest(
    val name: String? = null,
    val symbol: String? = null,
    val exchangeRateToBase: BigDecimal? = null
)

// ───────── Account DTOs ─────────

data class AccountResponse(
    val id: Long,
    val name: String,
    val type: AccountType,
    val balance: BigDecimal,
    val currency: CurrencyResponse,
    val description: String?,
    val color: String,
    val monobankAccountId: String?,
    val isMonobankLinked: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateAccountRequest(
    @field:NotBlank val name: String,
    val type: AccountType = AccountType.CHECKING,
    val initialBalance: BigDecimal = BigDecimal.ZERO,
    @field:Positive val currencyId: Long,
    val description: String? = null,
    val color: String = "#4F46E5"
)

data class UpdateAccountRequest(
    val name: String? = null,
    val type: AccountType? = null,
    val description: String? = null,
    val color: String? = null
)

// ───────── Category DTOs ─────────

data class CategoryResponse(
    val id: Long,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: String,
    val description: String?,
    val createdAt: LocalDateTime
)

data class CreateCategoryRequest(
    @field:NotBlank val name: String,
    val type: CategoryType,
    val icon: String = "tag",
    val color: String = "#6366F1",
    val description: String? = null
)

data class UpdateCategoryRequest(
    val name: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val description: String? = null
)

// ───────── Transaction DTOs ─────────

data class TransactionResponse(
    val id: Long,
    val account: AccountResponse,
    val category: CategoryResponse,
    val amount: BigDecimal,
    val type: TransactionType,
    val date: LocalDate,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreateTransactionRequest(
    @field:Positive val accountId: Long,
    @field:Positive val categoryId: Long,
    @field:Positive val amount: BigDecimal,
    val type: TransactionType,
    val date: LocalDate,
    val description: String? = null
)

data class UpdateTransactionRequest(
    val categoryId: Long? = null,
    val amount: BigDecimal? = null,
    val date: LocalDate? = null,
    val description: String? = null
)

// ───────── Transfer DTOs ─────────

data class TransferResponse(
    val id: Long,
    val fromAccount: AccountResponse,
    val toAccount: AccountResponse,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal,
    val date: LocalDate,
    val description: String?,
    val createdAt: LocalDateTime
)

data class CreateTransferRequest(
    @field:Positive val fromAccountId: Long,
    @field:Positive val toAccountId: Long,
    @field:Positive val fromAmount: BigDecimal,
    @field:Positive val toAmount: BigDecimal,
    val date: LocalDate,
    val description: String? = null
)

// ───────── Monobank DTOs ─────────
data class LinkMonobankRequest(
    @field:NotBlank val token: String,
    @field:NotBlank val monobankAccountId: String
)

data class MonobankClientInfo(
    val clientId: String,
    val name: String,
    val accounts: List<MonobankAccountInfo>
)

data class MonobankAccountInfo(
    val id: String,
    val currencyCode: Int,
    val balance: Long,
    val creditLimit: Long,
    val type: String,
    val maskedPan: List<String>
)

// ── Two-phase import ──
data class MonobankPreviewRequest(
    val accountId: Long,
    val dateFrom: Long,
    val dateTo: Long
)

data class MonobankPendingTransaction(
    val monobankId: String,
    val date: LocalDate,
    val description: String,
    val amount: BigDecimal,
    val type: TransactionType,
    val alreadyExists: Boolean,
    val existingTransactionId: Long? = null
)

data class MonobankPreviewResponse(
    val transactions: List<MonobankPendingTransaction>
)

data class MonobankConfirmItem(
    val monobankId: String,
    val categoryId: Long
)

data class MonobankConfirmRequest(
    val accountId: Long,
    val dateFrom: Long,
    val dateTo: Long,
    val items: List<MonobankConfirmItem>
)

data class MonobankImportResult(
    val imported: Int,
    val skipped: Int
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
    @field:NotBlank @field:Size(min = 6) val password: String,
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    val baseCurrencyId: Long? = null
)

data class JwtResponse(
    val token: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String
)

data class UserResponse(
    val id: Long,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: Role,
    val baseCurrency: CurrencyResponse?
)
