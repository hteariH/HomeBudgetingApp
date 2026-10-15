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
    val baseCurrency: CurrencyDto? = null,
    val householdId: Long? = null,
    val householdName: String? = null,
    val householdRole: String? = null
)

@Serializable
data class MemberDto(
    val userId: Long,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: String = "MEMBER",
    val joinedAt: String? = null
)

@Serializable
data class HouseholdDto(
    val id: Long,
    val name: String,
    val baseCurrency: CurrencyDto? = null,
    val members: List<MemberDto> = emptyList()
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
    val transferFee: String? = null,
    /** Who wrote it. Server-assigned and read-only: the push never sends it. */
    val createdByUserId: Long? = null
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

/** `/api/instance`: what a server offers, read before anyone signs in. */
@Serializable
data class InstanceDto(
    val name: String,
    val version: String = "",
    val apiVersion: Int,
    val setupRequired: Boolean = false,
    val registrationOpen: Boolean = false,
    val mailEnabled: Boolean = false,
    val bankProviders: List<String> = emptyList()
)
