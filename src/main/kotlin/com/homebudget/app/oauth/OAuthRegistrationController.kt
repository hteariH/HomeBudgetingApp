package com.homebudget.app.oauth

import com.fasterxml.jackson.databind.JsonNode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Dynamic client registration (RFC 7591). Open, as connectors require, so it is capped per
 * address the way [com.homebudget.app.security.AuthRateLimitFilter] caps sign-ins: per socket
 * address, in memory, coarse fixed windows swept at most once a window.
 */
@RestController
class OAuthRegistrationController(
    private val oauth: OAuthService,
    private val limiter: OAuthRegistrationLimiter
) {
    @PostMapping("/oauth/register")
    fun register(@RequestBody body: JsonNode, request: HttpServletRequest): ResponseEntity<Map<String, Any?>> {
        if (!oauth.available()) throw NoSuchElementException("Not found")
        if (!limiter.tryAcquire(request.remoteAddr ?: "unknown")) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", OAuthRegistrationLimiter.WINDOW.seconds.toString())
                .body(mapOf("error" to "too_many_requests", "error_description" to "Too many registrations; try again later"))
        }
        val uris = body.get("redirect_uris")
            ?.takeIf { it.isArray && it.all(JsonNode::isTextual) }
            ?.map(JsonNode::asText)
        val name = body.get("client_name")?.takeIf(JsonNode::isTextual)?.asText()
        val client = oauth.register(uris, name)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "client_id" to client.clientId,
                "client_name" to client.clientName,
                "redirect_uris" to client.redirectUriList(),
                "grant_types" to listOf("authorization_code", "refresh_token"),
                "response_types" to listOf("code"),
                "token_endpoint_auth_method" to "none",
                "client_id_issued_at" to client.createdAt.epochSecond
            )
        )
    }

    @ExceptionHandler(OAuthException::class)
    fun handle(e: OAuthException): ResponseEntity<Map<String, String?>> =
        ResponseEntity.badRequest().body(mapOf("error" to e.error, "error_description" to e.message))
}

/** At most [LIMIT] registrations per address per [WINDOW]. */
@Component
class OAuthRegistrationLimiter(private val clock: Clock) {
    companion object {
        const val LIMIT = 10
        val WINDOW: Duration = Duration.ofHours(1)
    }

    private data class Window(val startedAt: Instant, val count: Int)

    private val windows = ConcurrentHashMap<String, Window>()
    @Volatile private var lastSweep: Instant = Instant.EPOCH

    private fun Window.endedBy(now: Instant) = !startedAt.plus(WINDOW).isAfter(now)

    fun tryAcquire(address: String): Boolean {
        val now = Instant.now(clock)
        if (!lastSweep.plus(WINDOW).isAfter(now)) {
            lastSweep = now
            windows.values.removeIf { it.endedBy(now) }
        }
        val window = windows.compute(address) { _, existing ->
            if (existing == null || existing.endedBy(now)) Window(now, 1) else existing.copy(count = existing.count + 1)
        }!!
        return window.count <= LIMIT
    }
}
