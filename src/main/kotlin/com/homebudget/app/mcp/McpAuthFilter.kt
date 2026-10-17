package com.homebudget.app.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.security.CustomUserDetailsService
import com.homebudget.app.token.AccessTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

/**
 * The door to `/mcp` (registered for that path only, in [McpServerConfig]).
 *
 * MCP off → 404 with an empty body, as if there were no endpoint. No or bad token → 401 whose
 * challenge points at the OAuth protected-resource metadata when `APP_BASE_URL` is set. A
 * good token → its owner, loaded as a JWT sign-in loads it (current household included), in
 * the request attribute [McpPrincipalScope.ATTRIBUTE] and in the `SecurityContext`. Tool
 * calls over [McpRateLimiter.LIMIT] a minute → 429. A rejected token from an address that
 * has had [McpRateLimiter.FAILED_AUTH_LIMIT] tokens rejected this minute → 429 instead of 401. A session id that
 * [McpSessions] does not hold for this user → 404.
 */
class McpAuthFilter(
    private val settings: McpSettings,
    private val tokens: AccessTokenService,
    private val userDetails: CustomUserDetailsService,
    private val rateLimiter: McpRateLimiter,
    private val sessions: McpSessions,
    private val objectMapper: ObjectMapper,
    baseUrl: String
) : OncePerRequestFilter() {

    companion object {
        /** The largest JSON-RPC body accepted; the transport is given the same limit. */
        const val MAX_BODY_BYTES = 1_048_576
        private const val SESSION_HEADER = "Mcp-Session-Id"
    }

    private val challenge = if (baseUrl.isBlank()) "Bearer"
    else "Bearer resource_metadata=\"${baseUrl.trimEnd('/')}/.well-known/oauth-protected-resource\""

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (!settings.enabled()) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return
        }
        val bearer = request.getHeader("Authorization")
            ?.takeIf { it.regionMatches(0, "Bearer ", 0, 7, ignoreCase = true) }
            ?.substring(7)?.trim()
        val authenticated = bearer?.let { tokens.authenticate(it) }
        val principal = authenticated?.let {
            try {
                userDetails.loadUserByUsername(it.user.email)
            } catch (e: UsernameNotFoundException) {
                null // deleted after the token check; the cascade has taken the token too
            } catch (e: IllegalStateException) {
                null // in no household, which signing in cannot resolve either
            }
        }
        if (authenticated == null || principal == null) {
            // Only a token that was presented and refused counts against the address: a
            // request without one is the protocol's discovery step, and a valid token is
            // never held up by someone else's failures behind the same proxy or egress.
            if (bearer != null) {
                val address = request.remoteAddr ?: "unknown"
                if (rateLimiter.authBlocked(address)) {
                    response.status = 429
                    response.setHeader("Retry-After", "60")
                    return
                }
                rateLimiter.recordAuthFailure(address)
            }
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.setHeader("WWW-Authenticate", challenge)
            return
        }
        val userId = authenticated.user.id

        // A session belongs to whoever opened it; to anyone else it does not exist.
        val sessionId = request.getHeader(SESSION_HEADER)
        if (sessionId != null && !sessions.allows(sessionId, userId)) {
            response.status = HttpServletResponse.SC_NOT_FOUND
            return
        }

        var forwarded: HttpServletRequest = request
        if (request.method == "POST") {
            val body = request.inputStream.readNBytes(MAX_BODY_BYTES + 1)
            if (body.size > MAX_BODY_BYTES) {
                response.status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE
                return
            }
            val calls = toolCalls(body)
            if (calls > 0 && !rateLimiter.tryAcquire(authenticated.tokenId, calls)) {
                response.status = 429
                response.setHeader("Retry-After", "60")
                return
            }
            forwarded = CachedBodyRequest(request, body)
        }

        request.setAttribute(McpPrincipalScope.ATTRIBUTE, principal)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        // An initialize answers with a new session id: bound the moment the header is set, so
        // before the response is flushed and the client can use the session.
        val forwardedResponse = if (sessionId == null && request.method == "POST")
            SessionCapturingResponse(response) { sessions.bind(it, userId) } else response
        chain.doFilter(forwarded, forwardedResponse)

        when {
            sessionId != null && request.method == "GET" && request.isAsyncStarted ->
                sessions.track(authenticated.tokenId, sessionId, request.asyncContext)
            sessionId != null && request.method == "DELETE" && response.status in 200..299 ->
                sessions.unbind(sessionId)
        }
    }

    /** How many `tools/call` requests the JSON-RPC body holds (a batch may hold several). */
    private fun toolCalls(body: ByteArray): Int {
        val tree: JsonNode = try {
            objectMapper.readTree(body) ?: return 0
        } catch (e: Exception) {
            return 0 // the transport answers malformed JSON itself
        }
        val messages = if (tree.isArray) tree.toList() else listOf(tree)
        return messages.count { it.path("method").asText() == "tools/call" }
    }

    /** Hands the `Mcp-Session-Id` the transport sets to [onSession]. */
    private class SessionCapturingResponse(response: HttpServletResponse, private val onSession: (String) -> Unit) :
        HttpServletResponseWrapper(response) {

        override fun setHeader(name: String, value: String?) {
            capture(name, value)
            super.setHeader(name, value)
        }

        override fun addHeader(name: String, value: String?) {
            capture(name, value)
            super.addHeader(name, value)
        }

        private fun capture(name: String, value: String?) {
            if (value != null && name.equals(SESSION_HEADER, ignoreCase = true)) onSession(value)
        }
    }

    /** The request with its body already read, so the transport can read it again. */
    private class CachedBodyRequest(request: HttpServletRequest, private val body: ByteArray) :
        HttpServletRequestWrapper(request) {

        override fun getContentLength(): Int = body.size
        override fun getContentLengthLong(): Long = body.size.toLong()

        override fun getInputStream(): ServletInputStream {
            val bytes = ByteArrayInputStream(body)
            return object : ServletInputStream() {
                override fun read(): Int = bytes.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = bytes.read(b, off, len)
                override fun isFinished(): Boolean = bytes.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(listener: ReadListener) = throw UnsupportedOperationException()
            }
        }

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(inputStream, characterEncoding ?: Charsets.UTF_8.name()))
    }
}
