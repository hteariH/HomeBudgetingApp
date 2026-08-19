package com.personalmoney.core.auth

import com.personalmoney.core.util.AppLog
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.LoginRequestDto
import com.personalmoney.core.network.RegisterRequestDto
import io.ktor.client.plugins.ResponseException
import kotlinx.io.IOException
import kotlinx.coroutines.flow.StateFlow

/** What went wrong, in terms the UI can act on. */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String, val isNetwork: Boolean = false) : AuthResult
}

/**
 * Sign-in, registration and sign-out.
 *
 * Renewing an expired access token is deliberately *not* here: Ktor's Auth plugin owns
 * that (see `createBackendClient`), so no call site and no repository has to think about
 * token expiry.
 */
class AuthRepository constructor(
    private val api: HomeBudgetApi,
    private val tokenStore: TokenStore
) {
    val signedIn: StateFlow<Boolean> = tokenStore.signedIn
    val email: String? get() = tokenStore.email

    suspend fun login(email: String, password: String): AuthResult = runCatchingAuth {
        val response = api.login(LoginRequestDto(email.trim(), password))
        tokenStore.save(response.token, response.refreshToken, response.email)
        AuthResult.Success
    }

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): AuthResult = runCatchingAuth {
        api.register(
            RegisterRequestDto(email.trim(), password, firstName.trim(), lastName.trim())
        )
        // Registration does not return tokens, so sign in straight away to spare the
        // user typing the same credentials twice.
        login(email, password)
    }

    /**
     * Ends the session. The server call is best-effort: if it fails the local tokens
     * still go, because a user who taps sign-out expects to be signed out.
     */
    suspend fun logout() {
        try {
            api.logout()
        } catch (e: Exception) {
            AppLog.w(TAG, "Server logout failed, clearing locally anyway: ${e.message}")
        }
        tokenStore.clear()
    }

    private inline fun runCatchingAuth(block: () -> AuthResult): AuthResult = try {
        block()
    } catch (e: ResponseException) {
        AuthResult.Failure(describeHttp(e.response.status.value))
    } catch (e: IOException) {
        AuthResult.Failure(e.message ?: "Network error", isNetwork = true)
    } catch (e: Exception) {
        AuthResult.Failure(e.message ?: "Unexpected error")
    }

    private fun describeHttp(code: Int): String = when (code) {
        400 -> "Check the details and try again"
        401 -> "Wrong email or password"
        429 -> "Too many attempts. Wait a few minutes."
        in 500..599 -> "The server is having trouble. Try again later."
        else -> "Request failed ($code)"
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
