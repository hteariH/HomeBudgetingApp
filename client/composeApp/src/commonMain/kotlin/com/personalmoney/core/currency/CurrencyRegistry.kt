package com.personalmoney.core.currency

/**
 * Process-wide snapshot of currency metadata (symbol + decimals) for every currency the
 * user has in their database. Read synchronously by [com.personalmoney.core.domain.Money]
 * so non-ISO codes (BTC, ETH, user vouchers) get the symbol and precision the user chose.
 *
 * Populated from `CurrencyDao.observeAll()` on startup and refreshed whenever the user
 * edits a currency. It takes plain [Info] rather than the Room entity so it can live in
 * shared code - the web client has no Room at all.
 */
object CurrencyRegistry {

    data class Info(val code: String, val symbol: String, val decimals: Int)

    // A plain var, not an atomic reference: writes are a whole-map replacement from a
    // single collector, and readers only ever see the old or the new map.
    private var snapshot: Map<String, Info> = emptyMap()

    fun update(currencies: List<Info>) {
        snapshot = currencies.associateBy { it.code }
    }

    fun lookup(code: String): Info? = snapshot[code]

    fun symbolFor(code: String): String? = snapshot[code]?.symbol

    fun decimalsFor(code: String): Int? = snapshot[code]?.decimals

    /**
     * Falls back to a small table rather than ICU, which does not exist off the JVM.
     * This only matters for a code the user's own currency list does not contain.
     */
    fun decimalsOrFallback(code: String): Int =
        decimalsFor(code) ?: FALLBACK_DECIMALS[code.uppercase()] ?: 2

    private val FALLBACK_DECIMALS: Map<String, Int> = mapOf(
        // Zero-decimal currencies
        "JPY" to 0, "KRW" to 0, "VND" to 0, "CLP" to 0, "ISK" to 0, "HUF" to 0,
        "PYG" to 0, "RWF" to 0, "UGX" to 0, "VUV" to 0, "XAF" to 0, "XOF" to 0,
        "XPF" to 0, "KMF" to 0, "DJF" to 0, "GNF" to 0, "BIF" to 0,
        // Three-decimal currencies
        "BHD" to 3, "IQD" to 3, "JOD" to 3, "KWD" to 3, "LYD" to 3, "OMR" to 3, "TND" to 3,
        // Crypto the user is likely to add by hand
        "BTC" to 8, "ETH" to 8,
    )
}
