package com.personalmoney.core.homemoney

import com.personalmoney.core.database.entity.CurrencyEntity
import java.util.Currency

/**
 * HomeMoney's BalanceList returns a currency `shortname` that is a display symbol/abbreviation
 * (e.g. "$", "€", "₴", "ft"), NOT an ISO 4217 code. This resolves it to the ISO code the app uses,
 * so imported accounts reuse the existing seeded currencies (USD, EUR, UAH, HUF, …) instead of
 * creating junk duplicates named after the symbol.
 *
 * Built from the app's full currency table, so the symbol → code step is data-driven; only genuinely
 * ambiguous symbols (e.g. "$" shared by USD/MXN/ARS) need the [EXPLICIT] overrides.
 */
class HomeMoneyCurrencyResolver(currencies: List<CurrencyEntity>) {

    private val knownCodes: Set<String> = currencies.mapTo(HashSet()) { it.code.uppercase() }

    // symbol (trimmed, lower-cased) -> distinct ISO codes that use it
    private val codesBySymbol: Map<String, List<String>> =
        currencies.groupBy({ it.symbol.trim().lowercase() }, { it.code.uppercase() })
            .mapValues { (_, codes) -> codes.distinct() }

    /** Returns the ISO code for a HomeMoney shortname, or the upper-cased shortname if unknown. */
    fun resolve(shortname: String): String {
        val raw = shortname.trim()
        if (raw.isEmpty()) return raw
        val upper = raw.uppercase()

        // 1) Already an ISO code or a known custom code (e.g. the shortname really was "USD").
        if (upper in knownCodes || isIso(upper)) return upper

        // 2) Explicit override for shared symbols / regional abbreviations.
        EXPLICIT[raw.lowercase()]?.let { return it }

        // 3) Unambiguous symbol match against the currency table (€→EUR, ₴→UAH, ft→HUF, …).
        codesBySymbol[raw.lowercase()]?.let { codes -> if (codes.size == 1) return codes.first() }

        // 4) Unknown: keep as-is so it becomes a custom currency rather than failing the import.
        return upper
    }

    private fun isIso(code: String): Boolean =
        runCatching { Currency.getInstance(code) }.isSuccess

    companion object {
        /** Symbols shared by several currencies, plus common Cyrillic/Latin abbreviations. */
        private val EXPLICIT: Map<String, String> = mapOf(
            "$" to "USD", "us$" to "USD",
            "€" to "EUR",
            "₴" to "UAH", "грн" to "UAH", "грн." to "UAH",
            "₽" to "RUB", "руб" to "RUB", "руб." to "RUB", "р." to "RUB",
            "ft" to "HUF",
            "£" to "GBP",
            "¥" to "JPY",
            "₸" to "KZT",
            "zł" to "PLN", "zl" to "PLN",
            "kč" to "CZK",
            "kr" to "SEK",
            "₾" to "GEL",
            "₼" to "AZN",
            "₺" to "TRY",
            "₹" to "INR",
            "₪" to "ILS",
            "лв" to "BGN",
            "₿" to "BTC",
        )
    }
}
