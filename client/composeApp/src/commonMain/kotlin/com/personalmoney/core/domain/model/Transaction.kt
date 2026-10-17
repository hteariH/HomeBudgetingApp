package com.personalmoney.core.domain.model

import com.personalmoney.core.domain.TransactionType
import kotlin.time.Instant

/**
 * A single ledger row. See [Account] for why this is not the Room entity.
 *
 * Two amounts, deliberately. [amountMinor] is what was spent, in [currencyCode];
 * [accountAmountMinor] is what left the account, in the account's currency. They differ on
 * a foreign-currency purchase, and keeping both is what makes the account balance add up
 * without re-running an exchange rate that has since moved.
 *
 * A transfer is two of these rows sharing [transferGroupId] - a signed debit and a signed
 * credit - rather than a row type of its own.
 *
 * `id = 0` with a blank uuid means "not persisted yet". A screen building one for
 * [com.personalmoney.core.data.TransactionRepository.create] cannot invent an identity: on Android
 * the id comes from Room's autoincrement and the uuid is minted by the repository, and on
 * the web the server assigns both.
 */
data class Transaction(
    val id: Long = 0L,
    val uuid: String = "",
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long?,
    val amountMinor: Long,
    val currencyCode: String,
    val accountAmountMinor: Long,
    val fxRateToAccount: Double = 1.0,
    val occurredAt: Instant,
    val note: String? = null,
    val subscriptionId: Long? = null,
    val transferGroupId: String? = null,
    val transferCounterpartAccountId: Long? = null,
    val transferFeeMinor: Long? = null,
    /** The household member who wrote it; null for a row the server has not attributed. */
    val createdByUserId: Long? = null,
    /** The server-side receipt this entry was scanned from; its photo and items stay there. */
    val receiptUuid: String? = null,
    /** `APP`, or `MCP` when an assistant recorded it. The server owns it. */
    val source: String = "APP",
)
