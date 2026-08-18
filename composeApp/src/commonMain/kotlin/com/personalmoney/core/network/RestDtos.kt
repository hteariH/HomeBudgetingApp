package com.personalmoney.core.network

import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import kotlinx.serialization.Serializable

/**
 * The per-entity REST contract, as opposed to the sync contract in ApiDtos.
 *
 * The mobile clients push everything through /api/sync because they must work offline. A
 * browser tab cannot queue anything meaningfully, so the web client talks to these
 * endpoints directly and holds no local database.
 *
 * **Money arrives as a JSON string, never a number.** The server serialises every
 * BigDecimal as a string (JacksonConfig) so no amount ever touches a double. Decode into
 * String and convert through the money helpers - decoding into Double would reintroduce
 * exactly the rounding the server design avoids.
 */

@Serializable
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

@Serializable
data class RestCurrency(
    val id: Long,
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val sortOrder: Int = 0,
    val exchangeRateToBase: String? = null,
    val isBase: Boolean = false,
)

@Serializable
data class RestAccount(
    val id: Long,
    val uuid: String,
    val name: String,
    val type: String,
    val balance: String,
    val initialBalance: String,
    val currency: RestCurrency,
    val description: String? = null,
    val icon: String = "wallet",
    val color: String = "#4F46E5",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val includeInTotal: Boolean = true,
    val bankConnectionId: Long? = null,
    val externalAccountId: String? = null,
)

@Serializable
data class RestCategory(
    val id: Long,
    val uuid: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: String,
    val description: String? = null,
    val parentId: Long? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val budgetLimit: String? = null,
    val mccCodes: Set<Int> = emptySet(),
)

@Serializable
data class RestTransaction(
    val id: Long,
    val uuid: String,
    val account: RestAccount,
    val category: RestCategory? = null,
    val amount: String,
    val currencyCode: String,
    val accountAmount: String,
    val type: TransactionType,
    val date: String,
    val occurredAt: String,
    val description: String? = null,
    val transferGroupId: String? = null,
    val subscriptionId: Long? = null,
)

@Serializable
data class RestTransfer(
    val id: Long,
    val transferGroupId: String,
    val fromAccount: RestAccount,
    val toAccount: RestAccount,
    val fromAmount: String,
    val toAmount: String,
    val fee: String? = null,
    val exchangeRate: String,
    val date: String,
    val occurredAt: String,
    val description: String? = null,
)

// ── request bodies ───────────────────────────────────────────────────────────

@Serializable
data class CreateAccountRequest(
    val name: String,
    val type: String = "CHECKING",
    val initialBalance: String = "0",
    val currencyId: Long,
    val description: String? = null,
    val icon: String = "wallet",
    val color: String = "#4F46E5",
    val sortOrder: Int = 0,
    val includeInTotal: Boolean = true,
)

@Serializable
data class UpdateAccountRequest(
    val name: String? = null,
    val type: String? = null,
    val initialBalance: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val archived: Boolean? = null,
    val sortOrder: Int? = null,
    val includeInTotal: Boolean? = null,
)

@Serializable
data class CreateCategoryRequest(
    val name: String,
    val type: CategoryType,
    val icon: String = "category",
    val color: String = "#4F46E5",
    val description: String? = null,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val budgetLimit: String? = null,
)

@Serializable
data class CreateTransactionRequest(
    val accountId: Long,
    val categoryId: Long? = null,
    val amount: String,
    val type: TransactionType,
    val date: String,
    val description: String? = null,
)

@Serializable
data class CreateTransferRequest(
    val fromAccountId: Long,
    val toAccountId: Long,
    val fromAmount: String,
    val toAmount: String,
    val fee: String? = null,
    val date: String,
    val description: String? = null,
)
