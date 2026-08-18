package com.personalmoney.core.network

import com.personalmoney.core.auth.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor replaces Retrofit here because Retrofit is JVM-only: the same client has to run on
 * Android, iOS and wasm. Nothing in this file may reference a JVM or Android type.
 */

fun createJson(): Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

private const val TIMEOUT_MS = 20_000L

/**
 * Requests that mint tokens; the bearer header must never ride along on these.
 *
 * Matched on path segments rather than a string suffix so it holds regardless of how the
 * base URL is spelled (trailing slash, sub-path deployment).
 */
private fun isAuthEndpoint(segments: List<String>): Boolean {
    val parts = segments.filter { it.isNotEmpty() }
    if (parts.size < 2) return false
    return parts[parts.size - 2] == "auth" &&
        parts[parts.size - 1] in setOf("login", "register", "refresh")
}

/** The plain client, for third-party APIs that must not receive our bearer token. */
fun createPlainClient(
    engine: HttpClientEngine,
    json: Json,
    baseUrl: String? = null,
): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        connectTimeoutMillis = TIMEOUT_MS
        requestTimeoutMillis = TIMEOUT_MS
    }
    // INFO logs method, url and status. Never HEADERS or BODY: that would put bearer
    // tokens and balances into the device log.
    install(Logging) { level = LogLevel.INFO }
    if (baseUrl != null) defaultRequest { url(baseUrl) }
}

/**
 * The authenticated client for our own backend.
 *
 * Ktor's [Auth] plugin owns token refresh, which is why there is no interceptor pair any
 * more: it serialises concurrent refreshes internally, so a sync pass firing several
 * requests at once still spends exactly one refresh token. `markAsRefreshTokenRequest`
 * keeps the refresh call itself from recursing back through this plugin.
 */
fun createBackendClient(
    engine: HttpClientEngine,
    json: Json,
    baseUrl: String,
    tokenStore: TokenStore,
): HttpClient = HttpClient(engine) {
    expectSuccess = true
    install(ContentNegotiation) { json(json) }
    install(HttpTimeout) {
        connectTimeoutMillis = TIMEOUT_MS
        requestTimeoutMillis = TIMEOUT_MS
    }
    install(Logging) { level = LogLevel.INFO }
    defaultRequest { url(baseUrl) }

    install(Auth) {
        bearer {
            loadTokens {
                tokenStore.accessToken?.let { access ->
                    BearerTokens(access, tokenStore.refreshToken ?: "")
                }
            }
            refreshTokens {
                val refresh = tokenStore.refreshToken ?: return@refreshTokens null
                try {
                    val refreshed: JwtResponseDto = client.post("api/auth/refresh") {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequestDto(refresh))
                    }.body()
                    tokenStore.save(refreshed.token, refreshed.refreshToken, refreshed.email)
                    BearerTokens(refreshed.token, refreshed.refreshToken)
                } catch (e: Exception) {
                    // A revoked or expired refresh token is terminal. Clearing here is what
                    // sends the UI back to sign-in instead of retrying forever.
                    tokenStore.clear()
                    null
                }
            }
            sendWithoutRequest { request -> !isAuthEndpoint(request.url.pathSegments) }
        }
    }
}
