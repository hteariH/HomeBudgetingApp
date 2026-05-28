package com.personalmoney.core.currency

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Frankfurter is a free, no-key currency rates API backed by the European Central Bank.
 * Docs: https://www.frankfurter.app
 */
interface FrankfurterApi {
    @GET("latest")
    suspend fun latest(
        @Query("base") base: String,
        @Query("symbols") symbols: String? = null
    ): RatesResponse
}

@Serializable
data class RatesResponse(
    val amount: Double = 1.0,
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)
