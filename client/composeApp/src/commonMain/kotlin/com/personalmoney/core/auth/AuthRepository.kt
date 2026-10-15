package com.personalmoney.core.auth

import com.personalmoney.core.data.LocalDataWiper
import com.personalmoney.core.util.AppLog
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.LoginRequestDto
import com.personalmoney.core.network.RegisterRequestDto
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
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
    private val tokenStore: TokenStore,
    private val localData: LocalDataWiper,
) {
    val signedIn: StateFlow<Boolean> = tokenStore.signedIn
    val email: String? get() = tokenStore.email

    /** The server last signed in to, so the sign-in form can offer it again. */
    val lastServer: String? get() = tokenStore.serverUrl

    /**
     * Asks a typed address who it is, before anyone sends it a password.
     *
     * The answer tells the UI what to say: not a URL at all, nothing there, something there
     * that is not ours, or ours but speaking an API this build cannot.
     */
    suspend fun checkServer(input: String): ServerCheck {
        val base = ServerAddress.normalize(input) ?: return ServerCheck.Invalid
        return try {
            val instance = api.instance(base)
            if (instance.apiVersion !in ServerAddress.MIN_API_VERSION..ServerAddress.MAX_API_VERSION) {
                ServerCheck.Unsupported(instance.apiVersion)
            } else {
                ServerCheck.Ok(base, instance)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ServerCheck.Unreachable
        } catch (e: Exception) {
            // A 404, an HTML page, JSON of some other shape: something answered, and it is
            // not a PersonalMoney server.
            ServerCheck.NotPersonalMoney
        }
    }

    /** [baseUrl] is the address [checkServer] approved; it becomes the client's base. */
    suspend fun login(baseUrl: String, email: String, password: String): AuthResult = runCatchingAuth {
        tokenStore.serverUrl = baseUrl
        val response = api.login(LoginRequestDto(email.trim(), password))
        tokenStore.save(response.token, response.refreshToken, response.email)
        AuthResult.Success
    }

    suspend fun register(
        baseUrl: String,
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): AuthResult = runCatchingAuth {
        tokenStore.serverUrl = baseUrl
        api.register(
            RegisterRequestDto(email.trim(), password, firstName.trim(), lastName.trim())
        )
        // Registration does not return tokens, so sign in straight away to spare the
        // user typing the same credentials twice.
        login(baseUrl, email, password)
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
        endSession()
    }

    /**
     * Deletes the account on the server, then leaves this device as if it had never been
     * signed in.
     *
     * Unlike [logout] the server call is *not* best-effort. If it fails the account still
     * exists, and wiping the phone anyway would tell the user their data is gone while it
     * sits on the server waiting for the next sign-in - so the failure is reported and
     * nothing local is touched.
     */
    suspend fun deleteAccount(): AuthResult = runCatchingAuth {
        api.deleteMe()
        endSession()
        AuthResult.Success
    }

    /**
     * Local data first, tokens last. Clearing the tokens is what flips `signedIn` and
     * swaps the nav host for the sign-in screen, so doing it first would leave the
     * outgoing account's rows on screen for as long as the wipe takes.
     */
    private suspend fun endSession() {
        localData.wipe()
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
