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
 * Counted per socket address. Behind a reverse proxy that address is the proxy's until the
 * operator sets `server.forward-headers-strategy` (SERVER_FORWARD_HEADERS_STRATEGY=native),
 * which makes it the real client's - forwarding headers are never read here, because
 * whoever is guessing can put anything in them and get a fresh allowance per request.
 *
 * State is per-process and in memory, which is correct for the single-container
 * deployment. Windows that have ended are swept out, at most once per window, so the map
 * holds roughly the addresses seen in the last two windows rather than every one ever.
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
    @Volatile private var lastSweep: Instant = Instant.EPOCH

    private data class Window(val startedAt: Instant, val count: Int)

    private fun Window.endedBy(now: Instant) = startedAt.plusSeconds(windowSeconds).isBefore(now)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath ?: return true
        // Invitation tokens are guessable surface too.
        if (path.startsWith("/api/invites/")) return false
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
        sweep(now)

        val window = attempts.compute(key) { _, existing ->
            if (existing == null || existing.endedBy(now)) {
                Window(now, 1)
            } else {
                existing.copy(count = existing.count + 1)
            }
        }!!

        if (window.count > maxAttempts) {
            log.warn("Rate limit hit for {} on {}", key, loggablePath(request))
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
     * The path as it may be logged. An invitation link's token is a bearer secret, so
     * everything under /api/invites/ is logged as just that prefix plus a star.
     */
    private fun loggablePath(request: HttpServletRequest): String {
        val path = request.servletPath ?: return "unknown"
        return if (path.startsWith("/api/invites/")) "/api/invites/*" else path
    }

    private fun clientKey(request: HttpServletRequest): String = request.remoteAddr ?: "unknown"

    private fun sweep(now: Instant) {
        if (lastSweep.plusSeconds(windowSeconds).isAfter(now)) return
        lastSweep = now
        // Removes an entry only if it still holds the ended window it was read with.
        attempts.values.removeIf { it.endedBy(now) }
    }

    /** How many addresses are being counted right now. */
    internal fun trackedClients(): Int = attempts.size

    /** Lets a successful login clear the counter so a real user is not punished. */
    fun reset(request: HttpServletRequest) {
        attempts.remove(clientKey(request))
    }
}
