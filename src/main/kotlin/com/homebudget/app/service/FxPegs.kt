package com.homebudget.app.service

import java.math.BigDecimal

/**
 * Currencies whose rate is a definition rather than a quote.
 *
 * The clients' `core/currency/FxPegs` says the same thing for the same reasons; keep the
 * two in step. The short version: a dollar stablecoin has no entry in any fiat feed -
 * Frankfurter answers `invalid currency: USDT` and refuses the whole request - and needs
 * none, because one USDT is one dollar by construction. Without this an imported crypto
 * account is missing from every total without a word about it.
 *
 * Only the dollar-pegged coins. BTC and ETH have a real price and no feed here, so they
 * wait for a rate set by hand.
 */
object FxPegs {

    /** The currency conversions triangulate through; see [FxService.rate]. */
    const val HUB = "USD"

    /** 1 unit of the key is this many [HUB]. */
    val toHub: Map<String, BigDecimal> = mapOf(
        "USDT" to BigDecimal.ONE,
        "USDC" to BigDecimal.ONE,
    )

    /**
     * True for a currency whose rate is defined here.
     *
     * The daily refresh leaves these out of its request: asking a fiat feed for one is
     * what breaks the request for every other currency in it.
     */
    fun isPegged(code: String): Boolean = code.uppercase() in toHub
}
