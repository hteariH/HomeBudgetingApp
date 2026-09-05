package com.personalmoney.feature.homemoney

/**
 * What the server put in the URL after homemoney.ua sent the browser back.
 *
 * The outcome is a value here rather than a bare "did the redirect happen" flag because the
 * three endings are different things to say: consent refused is not the same as consent
 * given and the exchange still to come, and neither is worth reporting as silence. That
 * silence is exactly what shipped before - every ending navigated to the import screen,
 * which then showed a disconnected state with no explanation attached.
 */
sealed interface HomeMoneyRedirect {

    /**
     * Consent was given and [code] is what it produced.
     *
     * Still has to be exchanged for a token, and only a signed-in call can do it:
     * homemoney.ua returns no state, so the server's redirect handler has no way to know
     * whose consent this was.
     */
    data class Pending(val code: String) : HomeMoneyRedirect

    /** Consent refused, or homemoney.ua came back with no code at all. */
    data object Denied : HomeMoneyRedirect
}

/**
 * The redirect on its way from the web entry point to the ViewModel.
 *
 * A process-global, and read exactly once. The consent round trip leaves the page
 * entirely, so by the time anything composes there is no back stack to have carried an
 * argument on - only the query string the server redirected to, which `main.kt` reads
 * before the first frame. Taking it clears it: the code inside is single-use, and a second
 * attempt with the same one only earns an error from homemoney.ua.
 */
object PendingHomeMoneyRedirect {
    private var value: HomeMoneyRedirect? = null

    fun set(redirect: HomeMoneyRedirect?) {
        value = redirect
    }

    fun take(): HomeMoneyRedirect? = value.also { value = null }
}
