package com.personalmoney.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
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

    /**
     * Deletes the account and everything the server holds for it, irreversibly.
     *
     * Required by Play of any app that lets people register, which is why it is here
     * rather than waiting for someone to ask for it. The server erases rather than
     * tombstones, so there is nothing left for another device to sync down.
     */
    suspend fun deleteMe() {
        client.delete("api/users/me")
    }

    suspend fun currencies(): List<CurrencyDto> = client.get("api/currencies").body()

    // -- billing ---------------------------------------------------------------

    suspend fun billingStatus(): BillingStatusDto = client.get("api/billing/status").body()

    /**
     * Hands a Play purchase token to the server, which asks Google what it bought.
     *
     * Also how a purchase is restored: presenting a token that is still good re-grants
     * the entitlement whatever the server had recorded, so there is no separate call for
     * "I already paid on my other phone".
     */
    suspend fun registerPurchase(body: RegisterPurchaseRequestDto): BillingStatusDto =
        client.post("api/billing/play/purchase") { jsonBody(body) }.body()

    /** Asks the server to re-check this account's purchases with Google now. */
    suspend fun refreshPurchases(): BillingStatusDto =
        client.post("api/billing/play/refresh").body()

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
