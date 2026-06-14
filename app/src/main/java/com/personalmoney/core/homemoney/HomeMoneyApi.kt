package com.personalmoney.core.homemoney

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Minimal HomeMoney (homemoney.ua) API surface needed to import accounts and balances.
 *
 * The service is an ASP.NET ASMX endpoint; calling the HTTP-POST form variant
 * (`Content-Type: application/x-www-form-urlencoded`) returns JSON, so the responses deserialize
 * with kotlinx.serialization like any other JSON API.
 *
 * We use the OAuth password grant ([tokenPassword], not `Token`): the `Token` method also demands a
 * `redirect_uri` and is meant for the authorization-code flow, whereas `TokenPassword` accepts the
 * user's credentials directly — exactly what an in-app login needs.
 */
interface HomeMoneyApi {

    /**
     * Exchanges the user's HomeMoney credentials for an access token (valid for years).
     * On bad credentials the call still returns HTTP 200 with [TokenResponse.error] populated and a
     * null [TokenResponse.accessToken].
     */
    @FormUrlEncoded
    @POST("TokenPassword")
    suspend fun tokenPassword(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("username") username: String,
        @Field("password") password: String,
    ): TokenResponse

    /**
     * Returns the user's account tree: groups → accounts → per-currency balances.
     * An invalid token returns HTTP 200 with [BalanceListResponse.error] populated.
     */
    @FormUrlEncoded
    @POST("BalanceList")
    suspend fun balanceList(
        @Field("Token") token: String,
    ): BalanceListResponse
}
