package com.personalmoney.feature.auth

import com.personalmoney.core.auth.FakeTokenStore
import com.personalmoney.core.auth.RecordingWiper
import com.personalmoney.core.auth.ServerCheck
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
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * What the sign-in form does with the server address: nothing is sent to an address that
 * has not first said it is a PersonalMoney server, and the address is remembered.
 */
class AuthServerFlowTest {

    private fun authTest(body: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun instanceJson(registrationOpen: Boolean, setupRequired: Boolean = false) =
        """{"name":"PersonalMoney","version":"1.0.0","apiVersion":1,"setupRequired":$setupRequired,"registrationOpen":$registrationOpen,"mailEnabled":false,"bankProviders":[]}"""

    private class Rig(val viewModel: AuthViewModel, val tokens: FakeTokenStore, val paths: MutableList<String>)

    private fun rig(instance: String? = null, status: HttpStatusCode = HttpStatusCode.OK): Rig {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            val body = when {
                request.url.encodedPath == "/api/instance" -> instance ?: "{}"
                else -> """{"token":"a","refreshToken":"r","email":"first@example.com"}"""
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val tokens = FakeTokenStore(signedIn = false)
        val auth = authRepositoryOver(engine, tokens, RecordingWiper())
        return Rig(AuthViewModel(auth, NoOpSyncTrigger), tokens, paths)
    }

    private fun Rig.fillIn(address: String) {
        viewModel.onServerAddressChange(address)
        viewModel.onEmailChange("first@example.com")
        viewModel.onPasswordChange("a-real-password")
    }

    @Test
    fun `an address that is not our server stops the sign-in before any password is sent`() = authTest {
        val rig = rig(status = HttpStatusCode.NotFound)
        rig.fillIn("example.com")

        rig.viewModel.submit()
        val state = rig.viewModel.state.first { !it.busy }

        assertIs<ServerCheck.NotPersonalMoney>(state.serverProblem)
        assertEquals(listOf("/api/instance"), rig.paths)
        assertNull(rig.tokens.serverUrl)
    }

    @Test
    fun `a sign-in remembers the server it was made to`() = authTest {
        val rig = rig(instanceJson(registrationOpen = true))
        rig.fillIn("money.example.com")

        rig.viewModel.submit()
        val state = rig.viewModel.state.first { it.signedIn }

        assertEquals("https://money.example.com/", rig.tokens.serverUrl)
        // Shown again at the next sign-in, without the trailing slash it is stored with.
        assertEquals("https://money.example.com", state.serverAddress)
    }

    @Test
    fun `editing the address withdraws what was learned about the old one`() = authTest {
        val rig = rig(status = HttpStatusCode.NotFound)
        rig.fillIn("example.com")
        rig.viewModel.submit()
        rig.viewModel.state.first { !it.busy }

        rig.viewModel.onServerAddressChange("money.example.com")

        assertNull(rig.viewModel.state.value.serverProblem)
        assertNull(rig.viewModel.state.value.instance)
    }
}
