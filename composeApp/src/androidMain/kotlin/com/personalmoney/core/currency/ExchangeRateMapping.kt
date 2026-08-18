package com.personalmoney.core.currency

import com.personalmoney.core.database.entity.ExchangeRateEntity

/** Room rows to the shared [FxRate], which knows nothing about the database. */
fun ExchangeRateEntity.toFxRate(): FxRate = FxRate(baseCode, quoteCode, rate)

fun List<ExchangeRateEntity>.toFxRates(): List<FxRate> = map { it.toFxRate() }
