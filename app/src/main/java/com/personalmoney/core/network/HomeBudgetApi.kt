package com.personalmoney.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The HomeBudgeting server API.
 *
 * Only what this client needs: authentication, the reference data it cannot derive
 * locally, and the two sync endpoints. Everything else - accounts, categories,
 * transactions - travels through sync rather than through per-entity REST calls,
 * because the app has to work with no network at all and a REST call cannot be queued
 * meaningfully.
 */
interface HomeBudgetApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequestDto): JwtResponseDto

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequestDto)

    @POST("api/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): JwtResponseDto

    @POST("api/auth/logout")
    suspend fun logout()

    @GET("api/users/me")
    suspend fun me(): UserDto

    @GET("api/currencies")
    suspend fun currencies(): List<CurrencyDto>

    /** Server-side rates, so the phone does not hold a Frankfurter dependency of its own. */
    @GET("api/fx/rates")
    suspend fun fxRates(): List<FxRateDto>

    @GET("api/sync/pull")
    suspend fun pull(
        @Query("since") since: String,
        @Query("limit") limit: Int = 500
    ): SyncPullResponseDto

    @POST("api/sync/push")
    suspend fun push(@Body body: SyncPushRequestDto): SyncPushResponseDto
}
