package com.personalmoney.feature.auth

import com.personalmoney.core.auth.FakeTokenStore
import com.personalmoney.core.auth.RecordingWiper
import com.personalmoney.core.auth.authRepositoryOver
import com.personalmoney.core.sync.NoOpSyncTrigger
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * That the sign-in form does not carry one person's details into the next person's
 * session.
 *
 * [AuthViewModel] outlives the session it authenticated: signing out or deleting an
 * account swaps the nav host back to the auth screen with the same instance. It used to
 * still be holding the departing user's name, email and password, so on a shared phone
 * the next person arrived at a registration form filled in with somebody else's address -
 * which was visible on screen straight after an account deletion.
 */
class AuthFormResetTest {

    /**
     * Main has to run on the test's own scheduler, or the view model's coroutines are
     * never started at all.
     *
     * Note what this does *not* buy: `advanceUntilIdle` moves virtual time, and the mock
     * HTTP call completes on a real thread, so a sign-in cannot be waited for that way.
     * The test waits on the state itself instead - see [awaitSignedIn].
     */
    private fun authTest(body: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** A login response shaped like the server's, so `AuthRepository` can parse it. */
    private fun signInEngine() = MockEngine { request ->
        val body = if (request.url.encodedPath == "/api/instance") {
            """{"name":"PersonalMoney","version":"1.0.0","apiVersion":1,"setupRequired":false,"registrationOpen":true,"mailEnabled":false,"bankProviders":[]}"""
        } else {
            """{"token":"a","refreshToken":"r","email":"first@example.com"}"""
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

    private fun viewModel(engine: MockEngine = signInEngine()): Pair<AuthViewModel, FakeTokenStore> {
        val tokens = FakeTokenStore(signedIn = false)
        val auth = authRepositoryOver(engine, tokens, RecordingWiper())
        return AuthViewModel(auth, NoOpSyncTrigger) to tokens
    }

    /** Suspends until the sign-in coroutine has actually finished, however it got there. */
    private suspend fun AuthViewModel.awaitSignedIn(): AuthUiState =
        state.first { it.signedIn || it.error != null }

    @Test
    fun `a successful sign-in does not leave the password in memory`() = authTest {
        val (viewModel, _) = viewModel()
        viewModel.onServerAddressChange("https://money.example.com")
        viewModel.onEmailChange("first@example.com")
        viewModel.onPasswordChange("a-real-password")

        viewModel.submit()
        val state = viewModel.awaitSignedIn()

        assertNull(state.error, "the mock sign-in should have succeeded")
        assertEquals("", state.password)
        assertEquals("", state.email)
    }

    @Test
    fun `ending the session empties the form`() = authTest {
        // No HTTP here on purpose. What the view model reacts to is the token store's
        // signed-in flag, and driving that directly tests the reset without making the
        // test depend on a network round trip completing first.
        val (viewModel, tokens) = viewModel()
        tokens.save("a", "r", "first@example.com")
        viewModel.onEmailChange("first@example.com")
        viewModel.onPasswordChange("a-real-password")
        viewModel.onFirstNameChange("First")
        viewModel.onLastNameChange("User")
        viewModel.toggleMode()

        // What sign-out and account deletion both do, and the only signal the auth screen
        // gets that it is being shown to somebody new.
        tokens.clear()

        val state = viewModel.state.value
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals("", state.firstName)
        assertEquals("", state.lastName)
        assertFalse(state.signedIn)
        assertFalse(state.registerMode)
    }

    @Test
    fun `typing before the first sign-in survives`() = authTest {
        // The state flow replays its current value to a new collector. Reacting to that
        // replay rather than to a transition would clear the form the instant the screen
        // appeared, which is worse than the bug being fixed.
        val (viewModel, _) = viewModel()

        viewModel.onEmailChange("someone@example.com")
        viewModel.onPasswordChange("still-typing")

        assertEquals("someone@example.com", viewModel.state.value.email)
        assertEquals("still-typing", viewModel.state.value.password)
    }
}
