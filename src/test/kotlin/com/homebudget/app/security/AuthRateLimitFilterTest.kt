package com.homebudget.app.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@ExtendWith(OutputCaptureExtension::class)
class AuthRateLimitFilterTest {

    private class MovableClock(var now: Instant = Instant.parse("2026-10-15T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val clock = MovableClock()
    private val filter = AuthRateLimitFilter(ObjectMapper(), clock, maxAttempts = 2, windowSeconds = 60)

    private fun login(remoteAddr: String, forwardedFor: String? = null): Int {
        val request = MockHttpServletRequest("POST", "/api/auth/login").apply {
            servletPath = "/api/auth/login"
            this.remoteAddr = remoteAddr
            forwardedFor?.let {
                addHeader("X-Forwarded-For", it)
                addHeader("CF-Connecting-IP", it)
            }
        }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return response.status
    }

    @Test
    fun `a forged forwarding header does not buy a fresh allowance`() {
        repeat(2) { assertEquals(200, login("203.0.113.7")) }
        assertEquals(429, login("203.0.113.7"))
        // Whoever is guessing controls these headers; only the socket address counts
        // (a trusted proxy's real client address arrives through server.forward-headers-strategy).
        assertEquals(429, login("203.0.113.7", forwardedFor = "198.51.100.1"))
        assertEquals(429, login("203.0.113.7", forwardedFor = "198.51.100.2"))
    }

    @Test
    fun `addresses whose window has ended are forgotten`() {
        for (i in 1..5) login("192.0.2.$i")
        assertEquals(5, filter.trackedClients())

        clock.now = clock.now.plus(Duration.ofSeconds(61))
        assertEquals(200, login("192.0.2.100"))
        assertEquals(1, filter.trackedClients())
    }

    @Test
    fun `an invitation token never reaches the log`(output: CapturedOutput) {
        val token = "s3cret-invite-token-0123456789"
        fun preview(): Int {
            val request = MockHttpServletRequest("GET", "/api/invites/$token").apply {
                servletPath = "/api/invites/$token"
                remoteAddr = "203.0.113.50"
            }
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, MockFilterChain())
            return response.status
        }

        repeat(2) { assertEquals(200, preview()) }
        assertEquals(429, preview())
        assertTrue(output.all.contains("Rate limit hit"), "the limit trip should still be logged")
        assertFalse(output.all.contains(token), "the token must not be logged")
        assertTrue(output.all.contains("/api/invites/*"))
    }

    @Test
    fun `an address is allowed again after its window`() {
        repeat(2) { login("203.0.113.9") }
        assertEquals(429, login("203.0.113.9"))
        clock.now = clock.now.plus(Duration.ofSeconds(61))
        assertEquals(200, login("203.0.113.9"))
    }
}
