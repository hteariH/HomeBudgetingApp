package com.personalmoney.core.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.currency.CurrencyRegistry
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

actual object MoneyFormatter {

    actual fun format(money: Money): String {
        val locale = Locale.getDefault()
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
            return nf.format(money.toBigDecimal().toJava())
        }
        // 3) Last resort: prefix the raw code so the value remains unambiguous.
        return prefixFormat(money.currency.value, money.toBigDecimal(), money.decimals, locale)
    }

    actual fun formatPlain(money: Money): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault())
        nf.maximumFractionDigits = money.decimals
        nf.minimumFractionDigits = money.decimals
        return nf.format(money.toBigDecimal().toJava())
    }

    private fun prefixFormat(
        symbol: String,
        value: BigDecimal,
        decimals: Int,
        locale: Locale,
    ): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.maximumFractionDigits = decimals
        nf.minimumFractionDigits = decimals
        return "$symbol ${nf.format(value.toJava())}"
    }
}

/** Bridge to the JVM decimal type ICU needs. */
private fun BigDecimal.toJava(): java.math.BigDecimal = java.math.BigDecimal(toStringExpanded())
