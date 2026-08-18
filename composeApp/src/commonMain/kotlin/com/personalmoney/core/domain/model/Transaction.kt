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
 */
data class Transaction(
    val id: Long,
    val uuid: String,
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
)
