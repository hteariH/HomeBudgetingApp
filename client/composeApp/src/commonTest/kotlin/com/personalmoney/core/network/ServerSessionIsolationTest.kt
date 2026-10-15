package com.personalmoney.core.network

import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.auth.AuthResult
import com.personalmoney.core.auth.FakeTokenStore
import com.personalmoney.core.auth.RecordingWiper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * That one server's session token never reaches another server.
 *
 * Built over the real [createBackendClient] - the bare client other auth tests use has none
 * of the path rules, bearer handling or lazy base URL this is about.
 */
class ServerSessionIsolationTest {

    private class Seen(val host: String, val path: String, val authorization: String?)

    private val seen = mutableListOf<Seen>()

    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** Answers every server alike, and signs a login in as "token-<host>". */
    private val engine = MockEngine { request ->
        val host = request.url.host
        seen += Seen(host, request.url.encodedPath, request.headers[HttpHeaders.Authorization])
        val body = when (request.url.encodedPath) {
            "/api/instance" ->
                """{"name":"PersonalMoney","apiVersion":1,"registrationOpen":true}"""
            "/api/auth/login" ->
                """{"token":"token-$host","refreshToken":"refresh-$host","email":"someone@example.com"}"""
            else -> "[]"
        }
        respond(body, HttpStatusCode.OK, json)
    }

    private val tokens = FakeTokenStore(signedIn = true).apply {
        serverUrl = "https://a.example/"
        accessToken = "token-a.example"
    }

    private val api = HomeBudgetApi(createBackendClient(engine, createJson(), { tokens.serverUrl }, tokens))
    private val repository = AuthRepository(api, tokens, RecordingWiper())

    @Test
    fun `asking another address who it is never sends it our token`() = runTest {
        api.instance("https://other.example/")

        val request = seen.single()
        assertEquals("other.example", request.host)
        assertEquals("/api/instance", request.path)
        assertNull(request.authorization)
    }

    @Test
    fun `a new server's requests carry its own token, not the previous server's`() = runTest {
        api.currencies()
        assertEquals("a.example", seen.last().host)
        assertEquals("Bearer token-a.example", seen.last().authorization)

        repository.logout()
        val result = repository.login("https://b.example/", "someone@example.com", "a-real-password")
        assertEquals(AuthResult.Success, result)

        api.currencies()
        assertEquals("b.example", seen.last().host)
        assertEquals("Bearer token-b.example", seen.last().authorization)
        // Whatever else happened on the way, A's token went to A and only to A.
        assertEquals(
            emptyList(),
            seen.filter { it.host != "a.example" && it.authorization == "Bearer token-a.example" }.map { it.host }
        )
    }
}
