package com.personalmoney.core.currency

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Frankfurter is a free, no-key currency rates API.
 * The v2 API (api.frankfurter.dev) covers ~165 currencies — a superset of the legacy
 * ECB-only v1 endpoint — including currencies such as UAH that v1 omits.
 * Docs: https://frankfurter.dev
 */
class FrankfurterApi(private val client: HttpClient) {
    /**
     * Latest rates for [base] against the comma-separated [quotes].
     * Returns one entry per quote currency.
     */
    suspend fun rates(base: String, quotes: String? = null): List<FrankfurterRate> =
        client.get("v2/rates") {
            parameter("base", base)
            if (quotes != null) parameter("quotes", quotes)
        }.body()

    /**
     * Every currency it will quote.
     *
     * Worth a request of its own because one code it does not know makes the *rates* call
     * fail with 422 for every currency in it - so a single USDT account froze the whole
     * device's rates, silently and for good. Ask what it carries, send only that.
     */
    suspend fun currencies(): List<FrankfurterCurrency> = client.get("v2/currencies").body()
}

@Serializable
data class FrankfurterRate(
    val date: String,
    val base: String,
    val quote: String,
    val rate: Double
)

@Serializable
data class FrankfurterCurrency(
    @SerialName("iso_code") val isoCode: String
)
