package com.personalmoney.core.domain

import com.personalmoney.core.currency.CurrencyRegistry
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Represents an amount of money in a specific currency.
 * Stored as [minor] units (e.g. cents) using a [Long] to avoid floating-point errors.
 */
@JvmInline
value class CurrencyCode(val value: String) {
    override fun toString(): String = value
}

private val MIN_MINOR = BigDecimal(Long.MIN_VALUE)
private val MAX_MINOR = BigDecimal(Long.MAX_VALUE)

/**
 * Rounds to an integer and saturates to the [Long] range. Without this, a value past
 * Long.MAX_VALUE would silently wrap and flip the sign of an amount — corrupting balances.
 */
private fun BigDecimal.toClampedMinor(): Long {
    val rounded = setScale(0, RoundingMode.HALF_UP)
    return when {
        rounded > MAX_MINOR -> Long.MAX_VALUE
        rounded < MIN_MINOR -> Long.MIN_VALUE
        else -> rounded.toLong()
    }
}

data class Money(
    val minor: Long,
    val currency: CurrencyCode
) {
    val decimals: Int get() = CurrencyRegistry.decimalsOrFallback(currency.value)

    fun toBigDecimal(): BigDecimal =
        BigDecimal(minor).movePointLeft(decimals)

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
        copy(minor = BigDecimal(minor).multiply(factor).toClampedMinor())

    companion object {
        fun zero(currency: CurrencyCode) = Money(0L, currency)

        fun fromDecimal(value: BigDecimal, currency: CurrencyCode): Money {
            val decimals = CurrencyRegistry.decimalsOrFallback(currency.value)
            val minor = value.movePointRight(decimals).toClampedMinor()
            return Money(minor, currency)
        }

        fun fromMajor(value: Double, currency: CurrencyCode): Money =
            fromDecimal(BigDecimal.valueOf(value), currency)
    }
}

object MoneyFormatter {
    fun format(money: Money, locale: Locale = Locale.getDefault()): String {
        // 1) User-configured currency in the registry: format with the chosen symbol.
        val info = CurrencyRegistry.lookup(money.currency.value)
        if (info != null) {
            return prefixFormat(info.symbol, money.toBigDecimal(), info.decimals, locale)
        }
        // 2) Let ICU produce a locale-aware string when it knows the code.
        val icu = runCatching { Currency.getInstance(money.currency.value) }.getOrNull()
        if (icu != null) {
            val nf = NumberFormat.getCurrencyInstance(locale)
            nf.currency = icu
            val decimals = money.decimals
            nf.maximumFractionDigits = decimals
            nf.minimumFractionDigits = decimals
            return nf.format(money.toBigDecimal())
        }
        // 3) Last resort: prefix the raw code so the value remains unambiguous.
        return prefixFormat(money.currency.value, money.toBigDecimal(), money.decimals, locale)
    }

    fun formatPlain(money: Money, locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.maximumFractionDigits = money.decimals
        nf.minimumFractionDigits = money.decimals
        return nf.format(money.toBigDecimal())
    }

    private fun prefixFormat(
        symbol: String,
        value: BigDecimal,
        decimals: Int,
        locale: Locale
    ): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.maximumFractionDigits = decimals
        nf.minimumFractionDigits = decimals
        return "$symbol ${nf.format(value)}"
    }
}
