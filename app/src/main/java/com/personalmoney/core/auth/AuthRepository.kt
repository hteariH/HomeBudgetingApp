package com.personalmoney.core.auth

import android.util.Log
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.LoginRequestDto
import com.personalmoney.core.network.RefreshRequestDto
import com.personalmoney.core.network.RegisterRequestDto
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** What went wrong, in terms the UI can act on. */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String, val isNetwork: Boolean = false) : AuthResult
}

@Singleton
class AuthRepository @Inject constructor(
    // Provider, not a direct injection: the API client depends on the authenticator,
    // which depends on this repository. Breaking the cycle lazily is simpler than
    // splitting the client in two.
    private val api: Provider<HomeBudgetApi>,
    private val tokenStore: TokenStore
) {
    val signedIn: StateFlow<Boolean> = tokenStore.signedIn
    val email: String? get() = tokenStore.email

    suspend fun login(email: String, password: String): AuthResult = runCatchingAuth {
        val response = api.get().login(LoginRequestDto(email.trim(), password))
        tokenStore.save(response.token, response.refreshToken, response.email)
        AuthResult.Success
    }

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): AuthResult = runCatchingAuth {
        api.get().register(
            RegisterRequestDto(email.trim(), password, firstName.trim(), lastName.trim())
        )
        // Registration does not return tokens, so sign in straight away to spare the
        // user typing the same credentials twice.
        login(email, password)
    }

    /**
     * Exchanges the refresh token for a new access token.
     *
     * Returns null when the session is gone for good - a revoked or expired refresh
     * token - and clears local credentials so the UI can send the user to the login
     * screen instead of retrying forever.
     */
    suspend fun refreshSession(): String? {
        val refresh = tokenStore.refreshToken ?: return null
        return try {
            val response = api.get().refresh(RefreshRequestDto(refresh))
            tokenStore.save(response.token, response.refreshToken, response.email)
            response.token
        } catch (e: Exception) {
            Log.w(TAG, "Refresh failed, signing out: ${e.message}")
            tokenStore.clear()
            null
        }
    }

    /**
     * Ends the session. The server call is best-effort: if it fails the local tokens
     * still go, because a user who taps sign-out expects to be signed out.
     */
    suspend fun logout() {
        try {
            api.get().logout()
        } catch (e: Exception) {
            Log.w(TAG, "Server logout failed, clearing locally anyway: ${e.message}")
        }
        tokenStore.clear()
    }

    private inline fun runCatchingAuth(block: () -> AuthResult): AuthResult = try {
        block()
    } catch (e: retrofit2.HttpException) {
        AuthResult.Failure(describeHttp(e.code()))
    } catch (e: java.io.IOException) {
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
