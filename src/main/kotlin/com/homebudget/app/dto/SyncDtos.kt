package com.homebudget.app.dto

import com.homebudget.app.domain.AccountType
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.SubscriptionFrequency
import com.homebudget.app.domain.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Wire format for two-way sync.
 *
 * Everything here references other rows **by uuid, never by server id**. That is the
 * whole point: a phone that has been offline creates an account and three transactions
 * against it, none of which have server ids, and pushes them in one request. Ids only
 * come back in the response so the client can store them if it wants.
 *
 * Every record carries `updatedAt` and `deleted`, which is what makes deletions
 * replicable and last-write-wins decidable.
 */

interface SyncRecord {
    val uuid: String

    /**
     * Server clock, set by the server. Filled on pull; ignored on push.
     *
     * It is deliberately never the client's clock: this doubles as the pull cursor,
     * and a phone running a few minutes fast would otherwise plant rows in the future
     * that other clients skip past forever.
     */
    val updatedAt: Instant

    /**
     * On push: the [updatedAt] the client last received for this record, or null if it
     * is new. The server rejects the write when its own copy has moved on since -
     * optimistic concurrency, which needs no comparison between two machines' clocks.
     */
    val baseUpdatedAt: Instant?

    val deleted: Boolean
}

data class SyncAccount(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val name: String,
    val type: AccountType = AccountType.CHECKING,
    val currencyCode: String,
    val initialBalance: BigDecimal = BigDecimal.ZERO,
    val description: String? = null,
    val icon: String = "wallet",
    val color: String = "#4F46E5",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val includeInTotal: Boolean = true
) : SyncRecord

data class SyncCategory(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val name: String,
    val type: CategoryType,
    val icon: String = "tag",
    val color: String = "#6366F1",
    val description: String? = null,
    val parentUuid: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val budgetLimit: BigDecimal? = null,
    val rolloverEnabled: Boolean = false,
    val rolloverNegative: Boolean = false
) : SyncRecord

data class SyncSubscription(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val name: String,
    val accountUuid: String,
    val categoryUuid: String? = null,
    val amount: BigDecimal,
    val currencyCode: String,
    val frequency: SubscriptionFrequency,
    val intervalCount: Int = 1,
    val nextChargeDate: LocalDate,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val active: Boolean = true,
    val reminderMinutesBefore: Int? = null,
    val icon: String = "subscriptions",
    val color: String = "#008080",
    val note: String? = null
) : SyncRecord

data class SyncTransaction(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val accountUuid: String,
    val categoryUuid: String? = null,
    val amount: BigDecimal,
    val currencyCode: String,
    val accountAmount: BigDecimal,
    val fxRateToAccount: BigDecimal = BigDecimal.ONE,
    val type: TransactionType,
    val occurredAt: Instant,
    val description: String? = null,
    val subscriptionUuid: String? = null,
    val transferGroupId: String? = null,
    val transferCounterpartAccountUuid: String? = null,
    val transferFee: BigDecimal? = null
) : SyncRecord

data class SyncCurrencySetting(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val currencyCode: String,
    val active: Boolean,
    val sortOrder: Int = 0
) : SyncRecord

data class SyncTransactionSplit(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val transactionUuid: String,
    val categoryUuid: String? = null,
    val amount: BigDecimal,
    val note: String? = null,
    val sortOrder: Int = 0
) : SyncRecord

data class SyncGoal(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val name: String,
    val targetAmount: BigDecimal,
    val currencyCode: String,
    val currentAmount: BigDecimal = BigDecimal.ZERO,
    val targetDate: LocalDate? = null,
    val linkedAccountUuid: String? = null,
    val icon: String = "flag",
    val color: String = "#10B981",
    val archived: Boolean = false
) : SyncRecord

data class SyncTag(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val name: String,
    val color: String = "#6366F1"
) : SyncRecord

data class SyncTransactionTag(
    override val uuid: String,
    override val updatedAt: Instant = Instant.EPOCH,
    override val baseUpdatedAt: Instant? = null,
    override val deleted: Boolean = false,
    val transactionUuid: String,
    val tagUuid: String
) : SyncRecord

/**
 * Entity order in both directions is parents before children, which is also the order
 * a push must apply them in - a transaction cannot resolve an account uuid that has
 * not been written yet.
 */
data class SyncPayload(
    val currencySettings: List<SyncCurrencySetting> = emptyList(),
    val accounts: List<SyncAccount> = emptyList(),
    val categories: List<SyncCategory> = emptyList(),
    val subscriptions: List<SyncSubscription> = emptyList(),
    val transactions: List<SyncTransaction> = emptyList(),
    val transactionSplits: List<SyncTransactionSplit> = emptyList(),
    val goals: List<SyncGoal> = emptyList(),
    val tags: List<SyncTag> = emptyList(),
    val transactionTags: List<SyncTransactionTag> = emptyList()
) {
    fun size(): Int =
        currencySettings.size + accounts.size + categories.size + subscriptions.size +
        transactions.size + transactionSplits.size + goals.size + tags.size + transactionTags.size
}

data class SyncPullResponse(
    /** Cursor for the next pull. Server clock, never the client's. */
    val serverTime: Instant,
    /** True when the page was capped; pull again from [serverTime]. */
    val hasMore: Boolean,
    val changes: SyncPayload
)

data class SyncPushRequest(
    val changes: SyncPayload = SyncPayload()
)

enum class SyncRejectionReason {
    /** The incoming record is older than the server's copy; the server version wins. */
    STALE,

    /** A referenced uuid does not exist, or does not belong to the caller. */
    UNKNOWN_REFERENCE,

    /** The record itself was rejected by a domain rule. */
    INVALID
}

data class SyncRejection(
    val uuid: String,
    val entity: String,
    val reason: SyncRejectionReason,
    val message: String
)

data class SyncPushResponse(
    val serverTime: Instant,
    val applied: Int,
    /**
     * Records the server refused. A STALE rejection is normal - the client overwrites
     * its local copy from the next pull - while the others indicate a client bug.
     */
    val rejected: List<SyncRejection>
)
