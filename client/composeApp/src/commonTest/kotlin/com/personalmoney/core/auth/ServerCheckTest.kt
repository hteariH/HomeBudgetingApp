package com.personalmoney.core.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerCheckTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun repoAnswering(status: HttpStatusCode, body: String, json: Boolean = true) =
        authRepositoryOver(
            MockEngine { request ->
                assertEquals("/api/instance", request.url.encodedPath)
                respond(body, status, if (json) jsonHeaders else headersOf(HttpHeaders.ContentType, "text/html"))
            },
            FakeTokenStore(signedIn = false),
            RecordingWiper()
        )

    @Test
    fun `a PersonalMoney server answers with its instance`() = runTest {
        val check = repoAnswering(
            HttpStatusCode.OK,
            """{"name":"PersonalMoney","version":"1.0.0","apiVersion":1,"setupRequired":false,"registrationOpen":true,"mailEnabled":false,"bankProviders":[]}"""
        ).checkServer("money.example.com")
        assertIs<ServerCheck.Ok>(check)
        assertEquals("https://money.example.com/", check.baseUrl)
        assertEquals(true, check.instance.registrationOpen)
    }

    @Test
    fun `some other web server is recognised as not ours`() = runTest {
        assertIs<ServerCheck.NotPersonalMoney>(repoAnswering(HttpStatusCode.OK, "<html></html>", json = false).checkServer("example.com"))
        assertIs<ServerCheck.NotPersonalMoney>(repoAnswering(HttpStatusCode.NotFound, "{}").checkServer("example.com"))
    }

    @Test
    fun `a server speaking a newer API is refused with its version`() = runTest {
        val check = repoAnswering(
            HttpStatusCode.OK,
            """{"name":"PersonalMoney","version":"9.0.0","apiVersion":99,"setupRequired":false,"registrationOpen":false,"mailEnabled":false,"bankProviders":[]}"""
        ).checkServer("money.example.com")
        assertEquals(ServerCheck.Unsupported(99), check)
    }

    @Test
    fun `an address that is not a url never reaches the network`() = runTest {
        assertEquals(ServerCheck.Invalid, repoAnswering(HttpStatusCode.OK, "{}").checkServer("not a url"))
    }
}
