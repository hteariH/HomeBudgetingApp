package com.personalmoney.core.auth

import com.personalmoney.core.data.LocalDataWiper
import com.personalmoney.core.network.HomeBudgetApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The two things account deletion has to get right, neither of which is the happy path
 * being reachable.
 *
 * A delete that half-succeeds is worse than one that fails outright: telling somebody
 * their financial data is gone while the server still holds it is the failure that
 * matters, and it is invisible from the UI because the screen looks the same either way.
 */
class AccountDeletionTest {

    private class FakeTokenStore : TokenStore {
        private val _signedIn = MutableStateFlow(true)
        override val signedIn: StateFlow<Boolean> = _signedIn
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
        override var email: String? = "someone@example.com"
        var cleared = false

        override fun save(access: String, refresh: String, email: String?) {
            accessToken = access
            refreshToken = refresh
            this.email = email
            _signedIn.value = true
        }

        override fun clear() {
            cleared = true
            accessToken = null
            refreshToken = null
            email = null
            _signedIn.value = false
        }
    }

    private class RecordingWiper : LocalDataWiper {
        var wipes = 0
        override suspend fun wipe() {
            wipes++
        }
    }

    private fun repositoryOver(engine: MockEngine, tokens: TokenStore, wiper: LocalDataWiper) =
        AuthRepository(HomeBudgetApi(HttpClient(engine) { expectSuccess = true }), tokens, wiper)

    @Test
    fun `a deleted account takes the local copy and the session with it`() = runTest {
        val wiper = RecordingWiper()
        val tokens = FakeTokenStore()
        val auth = repositoryOver(MockEngine { respond("") }, tokens, wiper)

        val result = auth.deleteAccount()

        assertIs<AuthResult.Success>(result)
        assertEquals(1, wiper.wipes, "the on-device ledger has to go with the account")
        assertTrue(tokens.cleared, "the session has to end")
    }

    @Test
    fun `a server that refuses leaves the phone alone`() = runTest {
        val wiper = RecordingWiper()
        val tokens = FakeTokenStore()
        val auth = repositoryOver(
            MockEngine { respondError(HttpStatusCode.InternalServerError) },
            tokens,
            wiper,
        )

        val result = auth.deleteAccount()

        assertIs<AuthResult.Failure>(result)
        assertEquals(0, wiper.wipes, "the account still exists, so the local copy is not stale")
        assertFalse(tokens.cleared, "signing the user out would hide a failure they need to see")
    }

    @Test
    fun `signing out still clears the device even when the server call fails`() = runTest {
        val wiper = RecordingWiper()
        val tokens = FakeTokenStore()
        val auth = repositoryOver(
            MockEngine { respondError(HttpStatusCode.ServiceUnavailable) },
            tokens,
            wiper,
        )

        auth.logout()

        // The opposite trade from deletion, and deliberately so: sign-out is about this
        // device, and someone who taps it expects to be signed out whatever the network
        // is doing. Leaving the previous account's transactions in Room is what this
        // guards against - the next person to sign in would see them.
        assertEquals(1, wiper.wipes)
        assertTrue(tokens.cleared)
    }
}
