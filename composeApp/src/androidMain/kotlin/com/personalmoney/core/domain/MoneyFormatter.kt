package com.personalmoney.core.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.currency.CurrencyRegistry
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Locale-aware money formatting.
 *
 * Still Android-only on purpose. [Money] itself is shared, but this leans on ICU through
 * NumberFormat and java.util.Currency, and swapping that for a hand-rolled formatter would
 * quietly change what every existing user sees. It moves to shared code when the UI does,
 * behind an expect/actual that keeps this exact implementation on Android.
 */
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
            return nf.format(money.toJavaBigDecimal())
        }
        // 3) Last resort: prefix the raw code so the value remains unambiguous.
        return prefixFormat(money.currency.value, money.toBigDecimal(), money.decimals, locale)
    }

    fun formatPlain(money: Money, locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.maximumFractionDigits = money.decimals
        nf.minimumFractionDigits = money.decimals
        return nf.format(money.toJavaBigDecimal())
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
        return "$symbol ${nf.format(value.toJavaBigDecimal())}"
    }
}

/** Bridge to the JVM decimal type that ICU needs. */
private fun BigDecimal.toJavaBigDecimal(): java.math.BigDecimal =
    java.math.BigDecimal(toStringExpanded())

private fun Money.toJavaBigDecimal(): java.math.BigDecimal = toBigDecimal().toJavaBigDecimal()
