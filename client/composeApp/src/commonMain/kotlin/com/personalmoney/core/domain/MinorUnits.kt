package com.personalmoney.core.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode

/**
 * Decimal-to-minor-unit conversion, in one place.
 *
 * Call sites used to spell this out as movePointRight(d).setScale(0, HALF_UP).toLong(),
 * which is easy to get subtly wrong and, more importantly, unguarded: toLong() on a value
 * past Long.MAX_VALUE wraps and flips the sign of an amount. This saturates instead, the
 * same way [Money] does.
 */
fun BigDecimal.toMinorUnits(decimals: Int): Long {
    val scaled = moveDecimalPoint(decimals.coerceAtLeast(0))
        .roundToDigitPositionAfterDecimalPoint(0, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
    return when {
        scaled > MAX_MINOR_UNITS -> Long.MAX_VALUE
        scaled < MIN_MINOR_UNITS -> Long.MIN_VALUE
        else -> scaled.longValue(false)
    }
}

/** Rounds to [decimals] places, half away from zero — the money rounding used throughout. */
fun BigDecimal.roundTo(decimals: Int): BigDecimal =
    roundToDigitPositionAfterDecimalPoint(
        decimals.coerceAtLeast(0).toLong(),
        RoundingMode.ROUND_HALF_AWAY_FROM_ZERO,
    )

/** Minor units back to a decimal amount - the inverse of [toMinorUnits]. */
fun Long.toMajorUnits(decimals: Int): BigDecimal =
    BigDecimal.fromLong(this).moveDecimalPoint(-decimals.coerceAtLeast(0))

private val MIN_MINOR_UNITS = BigDecimal.fromLong(Long.MIN_VALUE)
private val MAX_MINOR_UNITS = BigDecimal.fromLong(Long.MAX_VALUE)

/**
 * Minor units as a fixed-scale decimal string, e.g. 1250 with 2 decimals -> "12.50".
 *
 * Done on the integer rather than through a decimal type because the scale has to survive
 * exactly: this is the sync wire format, and "12.5" and "12.50" must not become the same
 * string on the way out. No rounding happens here - there is nothing to round.
 */
fun Long.toDecimalString(decimals: Int): String {
    val places = decimals.coerceAtLeast(0)
    val negative = this < 0L
    // toString() on Long.MIN_VALUE cannot be negated, so work on the text.
    val digits = toString().removePrefix("-")
    val padded = digits.padStart(places + 1, '0')
    val whole = padded.dropLast(places)
    val fraction = padded.takeLast(places)
    val body = if (places == 0) whole else "$whole.$fraction"
    return if (negative) "-$body" else body
}

/** Display form with no trailing zeros: 12.50 -> "12.5", 12.00 -> "12". */
fun BigDecimal.toTrimmedString(): String {
    val text = toStringExpanded()
    if (!text.contains('.')) return text
    return text.trimEnd('0').trimEnd('.')
}

/**
 * Fixed-scale decimal string, e.g. 90 with 2 decimals -> "90.00".
 *
 * java.math.BigDecimal carried its scale around, so setScale(2) alone was enough to render
 * "90.00". bignum normalises instead and would print "90", which is a visible change in an
 * amount field. Routing through minor units restores the old output exactly.
 */
fun BigDecimal.toFixedString(decimals: Int): String =
    toMinorUnits(decimals).toDecimalString(decimals)
