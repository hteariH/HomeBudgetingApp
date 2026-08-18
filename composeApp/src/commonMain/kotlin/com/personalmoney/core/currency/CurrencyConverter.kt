package com.personalmoney.core.currency

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money

/** One stored rate, independent of how it is persisted. */
data class FxRate(val baseCode: String, val quoteCode: String, val rate: Double)

/**
 * Stateless snapshot of exchange rates. Build with [from] and use for synchronous conversions
 * inside flow combine blocks.
 *
 * Takes [FxRate] rather than the Room entity so it can live in shared code: the web client
 * reads rates over REST and has no database at all.
 */
class FxSnapshot private constructor(
    private val byPair: Map<Pair<String, String>, Double>
) {
    fun rate(base: String, quote: String): Double? {
        if (base == quote) return 1.0
        direct(base, quote)?.let { return it }
        inverse(base, quote)?.let { return it }
        val baseToUsd = direct(base, "USD") ?: inverse(base, "USD")
        val usdToQuote = direct("USD", quote) ?: inverse("USD", quote)
        return if (baseToUsd != null && usdToQuote != null) baseToUsd * usdToQuote else null
    }

    // Only trust positive, finite stored rates — a 0 or NaN rate would yield 0/Infinity
    // and poison every downstream conversion.
    private fun direct(base: String, quote: String): Double? =
        byPair[base to quote]?.takeIf { it.isFinite() && it > 0.0 }

    private fun inverse(base: String, quote: String): Double? =
        byPair[quote to base]?.takeIf { it.isFinite() && it > 0.0 }?.let { 1.0 / it }

    fun convert(money: Money, target: CurrencyCode): Money? {
        val r = rate(money.currency.value, target.value) ?: return null
        val decimals = CurrencyRegistry.decimalsOrFallback(target.value)
        val major = (money.toBigDecimal() * BigDecimal.fromDouble(r))
            .roundToDigitPositionAfterDecimalPoint(
                decimals.toLong(),
                RoundingMode.ROUND_HALF_AWAY_FROM_ZERO,
            )
        return Money.fromDecimal(major, target)
    }

    companion object {
        fun from(rates: List<FxRate>): FxSnapshot =
            FxSnapshot(
                rates.filter { it.rate.isFinite() && it.rate > 0.0 }
                    .associate { (it.baseCode to it.quoteCode) to it.rate }
            )

        val EMPTY = FxSnapshot(emptyMap())
    }
}
