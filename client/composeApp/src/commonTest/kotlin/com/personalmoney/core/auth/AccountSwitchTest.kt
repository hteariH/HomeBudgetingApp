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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountSwitchTest {

    private val tokens = """{"token":"a","refreshToken":"r","email":"me@example.com"}"""
    private fun engine() = MockEngine {
        respond(tokens, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

    @Test
    fun `signing in to another server wipes what the phone held`() = runTest {
        val store = FakeTokenStore(signedIn = false).apply { lastAccount = "https://old.example.com/|me@example.com" }
        val wiper = RecordingWiper()
        val auth = authRepositoryOver(engine(), store, wiper)

        assertTrue(auth.wouldSwitchAccount("https://new.example.com/", "me@example.com"))
        auth.login("https://new.example.com/", "me@example.com", "password-123")

        assertEquals(1, wiper.wipes)
        assertEquals("https://new.example.com/|me@example.com", store.lastAccount)
    }

    @Test
    fun `signing back in to the same account keeps the local data`() = runTest {
        val store = FakeTokenStore(signedIn = false).apply { lastAccount = "https://money.example.com/|me@example.com" }
        val wiper = RecordingWiper()
        val auth = authRepositoryOver(engine(), store, wiper)

        assertFalse(auth.wouldSwitchAccount("https://money.example.com/", "ME@example.com "))
        auth.login("https://money.example.com/", "me@example.com", "password-123")

        assertEquals(0, wiper.wipes)
    }

    @Test
    fun `an explicit sign-out leaves nothing to switch away from`() = runTest {
        val store = FakeTokenStore(signedIn = true).apply { lastAccount = "https://old.example.com/|me@example.com" }
        val wiper = RecordingWiper()
        val auth = authRepositoryOver(engine(), store, wiper)

        auth.logout()

        assertEquals(1, wiper.wipes)
        assertFalse(auth.wouldSwitchAccount("https://new.example.com/", "other@example.com"))
    }

    @Test
    fun `a rejected sign-in neither wipes the phone nor forgets the last account`() = runTest {
        val last = "https://old.example.com/|me@example.com"
        val store = FakeTokenStore(signedIn = false).apply { lastAccount = last }
        val wiper = RecordingWiper()
        val rejecting = MockEngine {
            respond("{}", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val auth = authRepositoryOver(rejecting, store, wiper)

        val result = auth.login("https://new.example.com/", "me@example.com", "wrong-password")

        assertTrue(result is AuthResult.Failure)
        assertEquals(0, wiper.wipes)
        assertEquals(last, store.lastAccount)
    }
}
