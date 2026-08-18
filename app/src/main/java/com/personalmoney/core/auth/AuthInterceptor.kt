package com.personalmoney.core.auth

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Attaches the bearer token to every call except the ones that mint tokens. */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (isAuthEndpoint(request)) return chain.proceed(request)

        val token = tokenStore.accessToken ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )
    }

    private fun isAuthEndpoint(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.endsWith("/api/auth/login") ||
            path.endsWith("/api/auth/register") ||
            path.endsWith("/api/auth/refresh")
    }
}

/**
 * Renews an expired access token and replays the request.
 *
 * OkHttp calls this on a 401 and retries automatically, which keeps every call site
 * free of refresh handling. The mutex matters: a sync pass fires several requests at
 * once, and without it each would burn a refresh token, and rotation would invalidate
 * the others mid-flight.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authRepository: Provider<AuthRepository>
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Give up rather than loop: the server said no twice.
        if (responseCount(response) >= 2) return null

        val staleToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        val newToken = runBlocking {
            mutex.withLock {
                val current = tokenStore.accessToken
                // Another thread already refreshed while this one waited.
                if (current != null && current != staleToken) return@withLock current
                authRepository.get().refreshSession()
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
