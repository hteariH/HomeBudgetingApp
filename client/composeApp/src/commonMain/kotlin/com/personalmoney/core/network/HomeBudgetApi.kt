package com.personalmoney.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * The HomeBudgeting server API.
 *
 * Only what this client needs: authentication, the reference data it cannot derive
 * locally, and the two sync endpoints. Everything else - accounts, categories,
 * transactions - travels through sync rather than through per-entity REST calls,
 * because the app has to work with no network at all and a REST call cannot be queued
 * meaningfully.
 *
 * The client is configured with `expectSuccess = true`, so a non-2xx response throws
 * a `ResponseException` rather than returning a body nobody checked.
 */
class HomeBudgetApi(private val client: HttpClient) {

    suspend fun login(body: LoginRequestDto): JwtResponseDto =
        client.post("api/auth/login") { jsonBody(body) }.body()

    suspend fun register(body: RegisterRequestDto) {
        client.post("api/auth/register") { jsonBody(body) }
    }

    suspend fun refresh(body: RefreshRequestDto): JwtResponseDto =
        client.post("api/auth/refresh") { jsonBody(body) }.body()

    suspend fun logout() {
        client.post("api/auth/logout")
    }

    suspend fun me(): UserDto = client.get("api/users/me").body()

    suspend fun currencies(): List<CurrencyDto> = client.get("api/currencies").body()

    /** Server-side rates, so the phone does not hold a Frankfurter dependency of its own. */
    suspend fun fxRates(): List<FxRateDto> = client.get("api/fx/rates").body()

    suspend fun pull(since: String, limit: Int = 500): SyncPullResponseDto =
        client.get("api/sync/pull") {
            parameter("since", since)
            parameter("limit", limit)
        }.body()

    suspend fun push(body: SyncPushRequestDto): SyncPushResponseDto =
        client.post("api/sync/push") { jsonBody(body) }.body()
}

private fun io.ktor.client.request.HttpRequestBuilder.jsonBody(body: Any) {
    contentType(ContentType.Application.Json)
    setBody(body)
}
