package com.personalmoney.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the HomeBudgeting server, mirroring its OpenAPI document.
 *
 * Money crosses the wire as a decimal **string**, not a number. The server stores
 * integer minor units and so does this app; a JSON number would go through a double on
 * the way and 0.1 + 0.2 is not a thing to do with somebody's balance. Conversion to and
 * from minor units happens in `SyncMappers`, using the currency's own scale.
 *
 * Every reference is a uuid, never a server id: rows created offline have no id yet.
 */

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val baseCurrencyId: Long? = null
)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class JwtResponseDto(
    val token: String,
    val refreshToken: String,
    val email: String,
    val firstName: String = "",
    val lastName: String = "",
    val role: String = "USER"
)

@Serializable
data class CurrencyDto(
    val id: Long,
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int = 2,
    val sortOrder: Int = 0
)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val firstName: String = "",
    val lastName: String = "",
    val baseCurrency: CurrencyDto? = null
)

// -- billing ------------------------------------------------------------------

/**
 * What the server says this account is entitled to, and what it could buy.
 *
 * The client never decides this. The server resolves the entitlement against its own
 * clock and against what Google said about the purchase, so a phone with a wound-forward
 * clock or a patched build gets the same answer as everyone else - and every limit is
 * enforced there regardless of what this says.
 */
@Serializable
data class BillingStatusDto(
    val premium: Boolean = false,
    /** ISO-8601. Null means the entitlement does not expire. */
    val expiresAt: String? = null,
    /** NONE, PLAY or MANUAL - see the server's `EntitlementSource`. */
    val source: String = "NONE",
    val autoRenewing: Boolean = false,
    val productId: String? = null,
    /**
     * Whether the server can take a purchase at all. False in a browser and on a
     * deployment with no Play service account, and the reason the paywall can say where
     * to buy instead of showing a button that cannot work.
     */
    val purchasesAccepted: Boolean = false,
    val products: List<String> = emptyList()
)

/** A purchase token from Play, for the server to check with Google. */
@Serializable
data class RegisterPurchaseRequestDto(
    val purchaseToken: String,
    val productId: String? = null
)

@Serializable
data class FxRateDto(
    val baseCode: String,
    val quoteCode: String,
    val rate: String,
    val rateDate: String,
    val source: String = "FRANKFURTER"
)

// ── Sync ────────────────────────────────────────────────────────────────────

@Serializable
data class SyncAccountDto(
    val uuid: String,
    val updatedAt: String? = null,
    val baseUpdatedAt: String? = null,
    val deleted: Boolean = false,
    val name: String,
    val type: String = "CHECKING",
    val currencyCode: String,
    val initialBalance: String = "0",
    val description: String? = null,
    val icon: String = "wallet",
    val color: String = "#4F46E5",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val includeInTotal: Boolean = true
)

@Serializable
data class SyncCategoryDto(
    val uuid: String,
    val updatedAt: String? = null,
    val baseUpdatedAt: String? = null,
    val deleted: Boolean = false,
    val name: String,
    val type: String,
    val icon: String = "tag",
    val color: String = "#6366F1",
    val description: String? = null,
    val parentUuid: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val budgetLimit: String? = null
)

@Serializable
data class SyncSubscriptionDto(
    val uuid: String,
    val updatedAt: String? = null,
    val baseUpdatedAt: String? = null,
    val deleted: Boolean = false,
    val name: String,
    val accountUuid: String,
    val categoryUuid: String? = null,
    val amount: String,
    val currencyCode: String,
    val frequency: String,
    val intervalCount: Int = 1,
    val nextChargeDate: String,
    val startDate: String,
    val endDate: String? = null,
    val active: Boolean = true,
    val reminderMinutesBefore: Int? = null,
    val icon: String = "subscriptions",
    val color: String = "#008080",
    val note: String? = null
)

@Serializable
data class SyncTransactionDto(
    val uuid: String,
    val updatedAt: String? = null,
    val baseUpdatedAt: String? = null,
    val deleted: Boolean = false,
    val accountUuid: String,
    val categoryUuid: String? = null,
    val amount: String,
    val currencyCode: String,
    val accountAmount: String,
    val fxRateToAccount: String = "1",
    val type: String,
    val occurredAt: String,
    val description: String? = null,
    val subscriptionUuid: String? = null,
    val transferGroupId: String? = null,
    val transferCounterpartAccountUuid: String? = null,
    val transferFee: String? = null
)

@Serializable
data class SyncCurrencySettingDto(
    val uuid: String,
    val updatedAt: String? = null,
    val baseUpdatedAt: String? = null,
    val deleted: Boolean = false,
    val currencyCode: String,
    val active: Boolean,
    val sortOrder: Int = 0
)

@Serializable
data class SyncPayloadDto(
    val currencySettings: List<SyncCurrencySettingDto> = emptyList(),
    val accounts: List<SyncAccountDto> = emptyList(),
    val categories: List<SyncCategoryDto> = emptyList(),
    val subscriptions: List<SyncSubscriptionDto> = emptyList(),
    val transactions: List<SyncTransactionDto> = emptyList()
) {
    val size: Int
        get() = currencySettings.size + accounts.size + categories.size +
            subscriptions.size + transactions.size
}

@Serializable
data class SyncPullResponseDto(
    val serverTime: String,
    val hasMore: Boolean = false,
    val changes: SyncPayloadDto = SyncPayloadDto()
)

@Serializable
data class SyncPushRequestDto(val changes: SyncPayloadDto)

@Serializable
data class SyncRejectionDto(
    val uuid: String,
    val entity: String,
    val reason: String,
    val message: String = ""
)

@Serializable
data class SyncPushResponseDto(
    val serverTime: String,
    val applied: Int = 0,
    val rejected: List<SyncRejectionDto> = emptyList()
)

@Serializable
data class ApiErrorDto(
    val status: Int = 0,
    val error: String = "",
    @SerialName("message") val message: String? = null
)
