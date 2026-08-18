package com.personalmoney.core.domain

import com.personalmoney.core.currency.CurrencyRegistry

/**
 * Intl is the browser's ICU: it already knows the group and decimal separators for the
 * user's locale, so the shape of this mirrors the Android implementation rather than
 * inventing a second set of formatting rules.
 */
@JsFun(
    "(value, decimals) => new Intl.NumberFormat(undefined, " +
        "{ minimumFractionDigits: decimals, maximumFractionDigits: decimals }).format(value)"
)
private external fun intlNumber(value: Double, decimals: Int): String

@JsFun(
    "(value, code, decimals) => { try { return new Intl.NumberFormat(undefined, " +
        "{ style: 'currency', currency: code, minimumFractionDigits: decimals, " +
        "maximumFractionDigits: decimals }).format(value); } catch (e) { return null; } }"
)
private external fun intlCurrency(value: Double, code: String, decimals: Int): String?

actual object MoneyFormatter {

    actual fun format(money: Money): String {
        val decimals = money.decimals
        val value = money.asDouble(decimals)
        // 1) A currency the user configured: use their chosen symbol, as on Android.
        val info = CurrencyRegistry.lookup(money.currency.value)
        if (info != null) {
            return "${info.symbol} ${intlNumber(money.asDouble(info.decimals), info.decimals)}"
        }
        // 2) Intl knows the ISO codes; it throws on anything else, hence the null.
        intlCurrency(value, money.currency.value, decimals)?.let { return it }
        // 3) Last resort: prefix the raw code so the value stays unambiguous.
        return "${money.currency.value} ${intlNumber(value, decimals)}"
    }

    actual fun formatPlain(money: Money): String {
        val decimals = money.decimals
        return intlNumber(money.asDouble(decimals), decimals)
    }
}

/**
 * Intl takes a Number, so the amount has to leave minor units here.
 *
 * Safe for display: a Double holds integers exactly to 2^53, which is far beyond any
 * balance a person has, and Intl rounds to [decimals] regardless. Arithmetic still happens
 * in minor units - this conversion exists only at the point of rendering.
 */
private fun Money.asDouble(decimals: Int): Double {
    var divisor = 1.0
    repeat(decimals) { divisor *= 10.0 }
    return minor.toDouble() / divisor
}
