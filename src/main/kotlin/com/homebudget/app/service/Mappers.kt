package com.homebudget.app.service

import com.homebudget.app.domain.*
import com.homebudget.app.dto.*
import java.math.BigDecimal
import java.time.ZoneOffset

// ───── Currency Mappers ─────
fun Currency.toResponse() = CurrencyResponse(
    id = id,
    code = code,
    name = name,
    symbol = symbol,
    decimals = decimals,
    sortOrder = sortOrder,
    exchangeRateToBase = exchangeRateToBase,
    isBase = isBase,
    createdAt = createdAt
)

// ───── Account Mappers ─────

/**
 * @param balanceMinor current balance from [AccountService.balances]. It is a required
 *   argument rather than a default so that a caller cannot accidentally serve a zero
 *   balance by forgetting to look it up.
 */
fun Account.toResponse(balanceMinor: Long) = AccountResponse(
    id = id,
    uuid = uuid,
    name = name,
    type = type,
    balance = MinorUnits.toDecimal(balanceMinor, currency.decimals),
    initialBalance = MinorUnits.toDecimal(initialBalanceMinor, currency.decimals),
    currency = currency.toResponse(),
    description = description,
    icon = icon,
    color = color,
    archived = archived,
    sortOrder = sortOrder,
    includeInTotal = includeInTotal,
    bankConnectionId = bankConnection?.id,
    externalAccountId = externalAccountId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ───── Category Mappers ─────

/**
 * @param baseDecimals scale of the user's base currency, which is what a budget limit
 *   is denominated in.
 */
fun Category.toResponse(baseDecimals: Int) = CategoryResponse(
    id = id,
    uuid = uuid,
    name = name,
    type = type,
    icon = icon,
    color = color,
    description = description,
    parentId = parent?.id,
    archived = archived,
    sortOrder = sortOrder,
    isDefault = isDefault,
    budgetLimit = budgetLimitMinor?.let { MinorUnits.toDecimal(it, baseDecimals) },
    mccCodes = mccCodes,
    createdAt = createdAt
)

fun User.toResponse() = UserResponse(
    id = id,
    uuid = uuid,
    email = email,
    firstName = firstName,
    lastName = lastName,
    role = role,
    emailVerified = emailVerified,
    baseCurrency = baseCurrency?.toResponse()
)

// ───── Transaction Mappers ─────
fun Transaction.toResponse(balanceMinor: Long, baseDecimals: Int) = TransactionResponse(
    id = id,
    uuid = uuid,
    account = account.toResponse(balanceMinor),
    category = category?.toResponse(baseDecimals),
    amount = MinorUnits.toDecimal(amountMinor, account.currency.decimals),
    currencyCode = currencyCode,
    accountAmount = MinorUnits.toDecimal(accountAmountMinor, account.currency.decimals),
    type = type,
    date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate(),
    occurredAt = occurredAt,
    description = description,
    transferGroupId = transferGroupId,
    subscriptionId = subscription?.id,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ───── Transfer Mappers ─────

/**
 * A transfer is rendered from its two legs. [this] is the debit leg (negative amount
 * on the source account) and carries the transfer's identity and fee.
 */
fun Transaction.toTransferResponse(
    credit: Transaction,
    debitBalanceMinor: Long,
    creditBalanceMinor: Long
): TransferResponse {
    val fromDecimals = account.currency.decimals
    val toDecimals = credit.account.currency.decimals
    val fromAmount = MinorUnits.toDecimal(-accountAmountMinor, fromDecimals)
    val toAmount = MinorUnits.toDecimal(credit.accountAmountMinor, toDecimals)
    return TransferResponse(
        id = id,
        transferGroupId = transferGroupId ?: "",
        fromAccount = account.toResponse(debitBalanceMinor),
        toAccount = credit.account.toResponse(creditBalanceMinor),
        fromAmount = fromAmount,
        toAmount = toAmount,
        fee = transferFeeMinor?.let { MinorUnits.toDecimal(it, fromDecimals) },
        exchangeRate = credit.fxRateToAccount,
        date = occurredAt.atZone(ZoneOffset.UTC).toLocalDate(),
        occurredAt = occurredAt,
        description = description,
        createdAt = createdAt
    )
}

// ───── Subscription Mappers ─────
fun Subscription.toResponse(balanceMinor: Long, baseDecimals: Int, decimals: Int) = SubscriptionResponse(
    id = id,
    uuid = uuid,
    name = name,
    account = account.toResponse(balanceMinor),
    category = category?.toResponse(baseDecimals),
    amount = MinorUnits.toDecimal(amountMinor, decimals),
    currencyCode = currencyCode,
    frequency = frequency,
    intervalCount = intervalCount,
    nextChargeDate = nextChargeDate,
    startDate = startDate,
    endDate = endDate,
    active = active,
    reminderMinutesBefore = reminderMinutesBefore,
    icon = icon,
    color = color,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Fallback scale when a user has not picked a base currency yet. */
const val DEFAULT_DECIMALS: Int = 2
