package com.personalmoney.core.currency

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Frankfurter is a free, no-key currency rates API.
 * The v2 API (api.frankfurter.dev) covers ~165 currencies — a superset of the legacy
 * ECB-only v1 endpoint — including currencies such as UAH that v1 omits.
 * Docs: https://frankfurter.dev
 */
interface FrankfurterApi {
    /**
     * Latest rates for [base] against the comma-separated [quotes].
     * Returns one entry per quote currency.
     */
    @GET("v2/rates")
    suspend fun rates(
        @Query("base") base: String,
        @Query("quotes") quotes: String? = null
    ): List<FrankfurterRate>
}

@Serializable
data class FrankfurterRate(
    val date: String,
    val base: String,
    val quote: String,
    val rate: Double
)
