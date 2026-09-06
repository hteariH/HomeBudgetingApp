package com.personalmoney.core.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
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

    @Test
    fun `a deleted account takes the local copy and the session with it`() = runTest {
        val wiper = RecordingWiper()
        val tokens = FakeTokenStore()
        val auth = authRepositoryOver(MockEngine { respond("") }, tokens, wiper)

        val result = auth.deleteAccount()

        assertIs<AuthResult.Success>(result)
        assertEquals(1, wiper.wipes, "the on-device ledger has to go with the account")
        assertTrue(tokens.cleared, "the session has to end")
    }

    @Test
    fun `a server that refuses leaves the phone alone`() = runTest {
        val wiper = RecordingWiper()
        val tokens = FakeTokenStore()
        val auth = authRepositoryOver(
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
        val auth = authRepositoryOver(
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
