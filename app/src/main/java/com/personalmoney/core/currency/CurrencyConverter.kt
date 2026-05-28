package com.personalmoney.core.currency

import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Stateless snapshot of exchange rates. Build with [from] and use for synchronous conversions
 * inside flow combine blocks.
 */
class FxSnapshot private constructor(
    private val byPair: Map<Pair<String, String>, Double>
) {
    fun rate(base: String, quote: String): Double? {
        if (base == quote) return 1.0
        byPair[base to quote]?.let { return it }
        byPair[quote to base]?.let { return 1.0 / it }
        val baseToUsd = byPair[base to "USD"] ?: byPair["USD" to base]?.let { 1.0 / it }
        val usdToQuote = byPair["USD" to quote] ?: byPair[quote to "USD"]?.let { 1.0 / it }
        return if (baseToUsd != null && usdToQuote != null) baseToUsd * usdToQuote else null
    }

    fun convert(money: Money, target: CurrencyCode): Money? {
        val r = rate(money.currency.value, target.value) ?: return null
        val decimals = runCatching {
            java.util.Currency.getInstance(target.value).defaultFractionDigits
        }.getOrDefault(2).coerceAtLeast(0)
        val major = money.toBigDecimal().multiply(BigDecimal(r))
            .setScale(decimals, RoundingMode.HALF_UP)
        return Money.fromDecimal(major, target)
    }

    companion object {
        fun from(rates: List<ExchangeRateEntity>): FxSnapshot =
            FxSnapshot(rates.associate { (it.baseCode to it.quoteCode) to it.rate })

        val EMPTY = FxSnapshot(emptyMap())
    }
}
