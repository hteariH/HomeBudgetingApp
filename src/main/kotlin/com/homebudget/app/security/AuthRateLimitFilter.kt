package com.homebudget.app.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Caps attempts against the credential endpoints.
 *
 * Without it, `/api/auth/login` is an unmetered password oracle - the single most
 * useful thing to point a script at on a public site. The window is deliberately
 * coarse: it exists to make bulk guessing impractical, not to be a precise quota.
 *
 * State is per-process and in memory, which is correct for the current single-container
 * deployment. Running more than one replica would need this moved to Redis, otherwise
 * the effective limit multiplies by the replica count.
 */
@Component
class AuthRateLimitFilter(
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    @Value("\${app.auth.rate-limit.max-attempts:10}") private val maxAttempts: Int,
    @Value("\${app.auth.rate-limit.window-seconds:300}") private val windowSeconds: Long
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val attempts = ConcurrentHashMap<String, Window>()

    private data class Window(val startedAt: Instant, val count: Int)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath ?: return true
        // The credential-guessing and mail-sending surface. Refresh and logout carry
        // an opaque secret, so they are not worth guessing at.
        return path !in setOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/resend-verification"
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val key = clientKey(request)
        val now = Instant.now(clock)

        val window = attempts.compute(key) { _, existing ->
            if (existing == null || existing.startedAt.plusSeconds(windowSeconds).isBefore(now)) {
                Window(now, 1)
            } else {
                existing.copy(count = existing.count + 1)
            }
        }!!

        if (window.count > maxAttempts) {
            log.warn("Rate limit hit for {} on {}", key, request.servletPath)
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            val retryAfter = windowSeconds - (now.epochSecond - window.startedAt.epochSecond)
            response.setHeader("Retry-After", retryAfter.coerceAtLeast(1).toString())
            objectMapper.writeValue(
                response.writer,
                mapOf(
                    "status" to HttpStatus.TOO_MANY_REQUESTS.value(),
                    "error" to "Too Many Requests",
                    "message" to "Too many attempts. Try again in ${retryAfter.coerceAtLeast(1)} seconds."
                )
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    /**
     * Behind the reverse proxy the socket address is the proxy's, so the forwarded
     * header is used when present. It is client-controlled and therefore spoofable -
     * this is a speed bump against bulk guessing, not an identity check.
     */
    private fun clientKey(request: HttpServletRequest): String =
        // CF-Connecting-IP first: the domain is proxied through Cloudflare, so
        // X-Forwarded-For carries Cloudflare's edge address and every user in the world
        // would share a single bucket - ten failed logins anywhere and nobody can sign
        // in. Falls back to the forwarded chain, then the socket, so the filter still
        // works if the proxy setup changes.
        request.getHeader("CF-Connecting-IP")?.trim()?.takeIf { it.isNotBlank() }
            ?: request.getHeader("X-Forwarded-For")?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr
            ?: "unknown"

    /** Lets a successful login clear the counter so a real user is not punished. */
    fun reset(request: HttpServletRequest) {
        attempts.remove(clientKey(request))
    }
}
