package com.personalmoney.core.data

import com.personalmoney.core.currency.FxPegs
import com.personalmoney.core.currency.FxRate
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestFxRate
import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * The shared rate contract, backed by REST. See [RestAccountRepository].
 *
 * The browser reads the rates the server already fetched rather than calling Frankfurter
 * itself: one request a day for the whole install instead of one per tab, no third-party
 * origin in the page's network traffic, and both clients converting with the same numbers.
 */
class RestExchangeRateRepository(private val api: RestApi) : ExchangeRateRepository {

    private val cache = MutableStateFlow<List<FxRate>>(emptyList())

    /**
     * See [LazyRefresh]. The screens are written against Room's guarantee - collect and
     * rows arrive - and a REST cache is empty until something asks the server.
     */
    private val lazyLoad = LazyRefresh { refresh() }

    /** The cache, but collecting it fetches first if nothing has yet. */
    private val loaded get() = cache.loadingOnce(lazyLoad)

    override fun observeAll(): Flow<List<FxRate>> = loaded

    override suspend fun refresh() {
        cache.value = api.fxRates().mapNotNull { it.toFxRate() }
    }

    /**
     * Read from the cache rather than asked for on its own: the server sends a user's
     * overrides down the same list as the fetched rates, and a pair quoted against
     * [FxPegs.HUB] is only ever one of theirs - the nightly job quotes *from* USD, never
     * into it.
     */
    override suspend fun handRate(code: String): Double? = loaded.first()
        .firstOrNull { it.baseCode == code.uppercase() && it.quoteCode == FxPegs.HUB }
        ?.rate

    override suspend fun setHandRate(code: String, perUsd: Double?) {
        if (perUsd == null || !perUsd.isFinite() || perUsd <= 0.0) {
            api.clearFxOverride(code.uppercase())
        } else {
            api.setFxOverride(code.uppercase(), perUsd.toString())
        }
        refresh()
    }
}

/**
 * Null for a rate that will not parse. Dropping the row is right here: `FxSnapshot` falls
 * back to a triangulated rate or reports that it cannot convert, whereas a 0.0 would
 * silently turn every amount in that currency into nothing.
 */
private fun RestFxRate.toFxRate(): FxRate? {
    val parsed = rate.toDoubleOrNull()
    if (parsed == null || !parsed.isFinite() || parsed <= 0.0) {
        AppLog.w("RestExchangeRateRepository", "Dropping unusable rate $baseCode/$quoteCode: $rate")
        return null
    }
    return FxRate(baseCode = baseCode, quoteCode = quoteCode, rate = parsed)
}
