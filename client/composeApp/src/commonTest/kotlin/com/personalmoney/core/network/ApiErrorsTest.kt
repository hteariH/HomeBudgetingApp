package com.personalmoney.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the user is shown when a request fails.
 *
 * The bodies below are copied verbatim from the running server - a real 422 from posting a
 * bad Monobank token, and a real 404 - because the point of [serverMessage] is that the
 * server's own sentence reaches the user, and only its actual wire format proves that.
 */
class ApiErrorsTest {

    private fun clientReturning(status: HttpStatusCode, body: String) = HttpClient(
        MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
    ) {
        // The same setting the real clients use; it is what turns a failure into the
        // exception this function reads.
        expectSuccess = true
    }

    private suspend fun messageFrom(status: HttpStatusCode, body: String): String? =
        runCatching { clientReturning(status, body).get("http://localhost/api/bank/connections") }
            .exceptionOrNull()!!
            .serverMessage()

    @Test
    fun `a rejected bank token reaches the user in the bank's own words`() = runTest {
        val body = """
            {"timestamp":"2026-08-19T21:55:27.9500155","status":422,
             "error":"Bank Rejected Credentials","message":"Monobank rejected the token"}
        """.trimIndent()

        assertEquals(
            "Monobank rejected the token",
            messageFrom(HttpStatusCode.UnprocessableEntity, body),
        )
    }

    @Test
    fun `a not-found says what was not found`() = runTest {
        val body = """
            {"timestamp":"2026-08-19T21:55:27.5375132","status":404,
             "error":"Not Found","message":"Bank connection not found: 999999"}
        """.trimIndent()

        assertEquals(
            "Bank connection not found: 999999",
            messageFrom(HttpStatusCode.NotFound, body),
        )
    }

    /**
     * A 500's `message` is a raw exception string. Null here is what sends the caller to a
     * localised sentence instead of showing somebody a Java stack frame.
     */
    @Test
    fun `an internal error is not repeated to the user`() = runTest {
        val body =
            """{"status":500,"error":"Internal Server Error","message":"could not execute statement"}"""

        assertNull(messageFrom(HttpStatusCode.InternalServerError, body))
    }

    @Test
    fun `a body that is not the server's error shape yields nothing`() = runTest {
        assertNull(messageFrom(HttpStatusCode.BadRequest, "<html>gateway error</html>"))
    }

    /** Not every failure is an HTTP response - a dropped connection has no body at all. */
    @Test
    fun `a non-HTTP failure has no server message`() = runTest {
        assertNull(RuntimeException("offline").serverMessage())
    }

    @Test
    fun `the free plan's refusal is recognised as one, not just read`() = runTest {
        // 402 is the only status the server uses for a plan limit, and a screen keys the
        // "see Premium" action off it - so this is what stops that action appearing on an
        // unrelated failure, or failing to appear on this one.
        val body = """
            {"status":402,"error":"Plan Limit Reached",
             "message":"The free plan covers 5 accounts. Subscribe to add more."}
        """.trimIndent()

        val failure = runCatching {
            clientReturning(HttpStatusCode.PaymentRequired, body).get("http://localhost/api/accounts")
        }.exceptionOrNull()!!

        assertTrue(failure.isPlanLimit())
        assertFalse(
            runCatching { clientReturning(HttpStatusCode.NotFound, body).get("http://localhost/api/accounts") }
                .exceptionOrNull()!!
                .isPlanLimit()
        )
    }
}
