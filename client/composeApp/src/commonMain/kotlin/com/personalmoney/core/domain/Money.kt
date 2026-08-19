package com.personalmoney.core.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.personalmoney.core.currency.CurrencyRegistry
import kotlin.jvm.JvmInline

/**
 * Represents an amount of money in a specific currency.
 * Stored as [minor] units (e.g. cents) using a [Long] to avoid floating-point errors.
 *
 * Arithmetic goes through ionspin bignum rather than java.math.BigDecimal so the same
 * money rules apply on every platform. Amounts are integers in minor units, so the only
 * work a decimal type does here is scaling and FX multiplication.
 */
@JvmInline
value class CurrencyCode(val value: String) {
    override fun toString(): String = value
}

private val MIN_MINOR = BigDecimal.fromLong(Long.MIN_VALUE)
private val MAX_MINOR = BigDecimal.fromLong(Long.MAX_VALUE)

/**
 * Rounds to an integer and saturates to the [Long] range. Without this, a value past
 * Long.MAX_VALUE would silently wrap and flip the sign of an amount — corrupting balances.
 */
private fun BigDecimal.toClampedMinor(): Long {
    val rounded = roundToDigitPositionAfterDecimalPoint(0, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
    return when {
        rounded > MAX_MINOR -> Long.MAX_VALUE
        rounded < MIN_MINOR -> Long.MIN_VALUE
        else -> rounded.longValue(false)
    }
}

data class Money(
    val minor: Long,
    val currency: CurrencyCode
) {
    val decimals: Int get() = CurrencyRegistry.decimalsOrFallback(currency.value)

    fun toBigDecimal(): BigDecimal =
        BigDecimal.fromLong(minor).moveDecimalPoint(-decimals)

    fun isZero(): Boolean = minor == 0L
    fun isNegative(): Boolean = minor < 0L

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add different currencies: $currency vs ${other.currency}" }
        return copy(minor = minor + other.minor)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract different currencies: $currency vs ${other.currency}" }
        return copy(minor = minor - other.minor)
    }

    operator fun unaryMinus(): Money =
        copy(minor = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else -minor)

    operator fun times(factor: BigDecimal): Money =
        copy(minor = (BigDecimal.fromLong(minor) * factor).toClampedMinor())

    companion object {
        fun zero(currency: CurrencyCode) = Money(0L, currency)

        fun fromDecimal(value: BigDecimal, currency: CurrencyCode): Money {
            val decimals = CurrencyRegistry.decimalsOrFallback(currency.value)
            val minor = value.moveDecimalPoint(decimals).toClampedMinor()
            return Money(minor, currency)
        }

        fun fromMajor(value: Double, currency: CurrencyCode): Money =
            fromDecimal(BigDecimal.fromDouble(value), currency)
    }
}
