package com.personalmoney.core.data

import com.personalmoney.core.currency.FxRate
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestFxRate
import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The shared rate contract, backed by REST. See [RestAccountRepository].
 *
 * The browser reads the rates the server already fetched rather than calling Frankfurter
 * itself: one request a day for the whole install instead of one per tab, no third-party
 * origin in the page's network traffic, and both clients converting with the same numbers.
 */
class RestExchangeRateRepository(private val api: RestApi) : ExchangeRateRepository {

    private val cache = MutableStateFlow<List<FxRate>>(emptyList())

    override fun observeAll(): Flow<List<FxRate>> = cache

    override suspend fun refresh() {
        cache.value = api.fxRates().mapNotNull { it.toFxRate() }
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
