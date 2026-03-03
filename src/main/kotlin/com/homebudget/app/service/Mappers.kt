package com.homebudget.app.service

import com.homebudget.app.domain.*
import com.homebudget.app.dto.*

// ───── Currency Mappers ─────
fun Currency.toResponse() = CurrencyResponse(
    id = id,
    code = code,
    name = name,
    symbol = symbol,
    exchangeRateToBase = exchangeRateToBase,
    isBase = isBase,
    createdAt = createdAt
)

// ───── Account Mappers ─────
fun Account.toResponse() = AccountResponse(
    id = id,
    name = name,
    type = type,
    balance = balance,
    currency = currency.toResponse(),
    description = description,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ───── Category Mappers ─────
fun Category.toResponse() = CategoryResponse(
    id = id,
    name = name,
    type = type,
    icon = icon,
    color = color,
    description = description,
    createdAt = createdAt
)

// ───── Transaction Mappers ─────
fun Transaction.toResponse() = TransactionResponse(
    id = id,
    account = account.toResponse(),
    category = category.toResponse(),
    amount = amount,
    type = type,
    date = date,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ───── Transfer Mappers ─────
fun Transfer.toResponse() = TransferResponse(
    id = id,
    fromAccount = fromAccount.toResponse(),
    toAccount = toAccount.toResponse(),
    fromAmount = fromAmount,
    toAmount = toAmount,
    exchangeRate = exchangeRate,
    date = date,
    description = description,
    createdAt = createdAt
)
