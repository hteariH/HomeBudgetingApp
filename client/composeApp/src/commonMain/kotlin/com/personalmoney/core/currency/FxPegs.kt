package com.personalmoney.core.currency

/**
 * Currencies whose rate is a definition rather than a quote.
 *
 * A dollar stablecoin is not a currency any rates feed carries - Frankfurter answers
 * `invalid currency: USDT` and refuses the whole request - and it is not a currency
 * anybody needs a feed for either: one USDT is one dollar by construction, which is the
 * entire point of the thing. Without this an imported crypto account is simply absent
 * from every total, silently, because [FxSnapshot.rate] has no way to reach it.
 *
 * Only the dollar-pegged coins belong here. BTC and ETH have a real, moving price and no
 * free feed in this app, so they stay unconvertible until somebody sets a rate by hand -
 * a number that is wrong in an interesting way beats a peg that is wrong on purpose.
 *
 * These are defaults, not overrides: [FxSnapshot.from] drops the peg for any code the
 * stored rates already mention, so a hand-set USDT rate wins. A depegged coin is
 * somebody's real problem, and the app must not argue with them about it.
 */
object FxPegs {

    /** The currency every triangulated conversion hops through; see [FxSnapshot.rate]. */
    const val HUB = "USD"

    /** 1 unit of the key is this many [HUB]. */
    val toHub: Map<String, Double> = mapOf(
        "USDT" to 1.0,
        "USDC" to 1.0,
    )

    /**
     * True for a currency whose rate is defined here.
     *
     * Callers that fetch rates use this to leave the code out of the request: asking a
     * fiat feed for it is what breaks the request for every other currency in it.
     */
    fun isPegged(code: String): Boolean = code.uppercase() in toHub
}
