package com.homebudget.app.mcp

import com.homebudget.app.token.TokenRevokedEvent
import jakarta.servlet.AsyncContext
import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import org.slf4j.LoggerFactory
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** MCP was turned off; every open stream ends. */
object McpDisabledEvent

/**
 * Who owns each MCP session, and the SSE streams that are open.
 *
 * The SDK identifies a session by its `Mcp-Session-Id` alone. [McpAuthFilter] binds each new
 * session to the user whose token opened it and refuses it to anyone else, as if it did not
 * exist (404). A session id this class does not know is refused too: the client then starts
 * a new session, as the protocol prescribes.
 *
 * A bound session idle for [IDLE] with no open stream is forgotten. The SDK evicts sessions
 * idle for a whole sweep interval (30 minutes, checked every 30 minutes), so by then it has
 * forgotten the session as well, or is about to.
 *
 * Open GET streams are remembered with the token that opened them, so revoking that token
 * or turning MCP off ends them at once instead of when the client hangs up. Completing the
 * async context runs the SDK's own listener, which releases the stream.
 */
@Component
class McpSessions(private val clock: Clock) {
    companion object {
        val IDLE: Duration = Duration.ofHours(1)
        private val SWEEP: Duration = Duration.ofMinutes(1)
        private val log = LoggerFactory.getLogger(McpSessions::class.java)
    }

    private class Binding(val userId: Long, @Volatile var lastSeen: Instant)
    private class Stream(val tokenId: Long, val sessionId: String, val context: AsyncContext)

    private val bindings = ConcurrentHashMap<String, Binding>()
    private val streams: MutableSet<Stream> = ConcurrentHashMap.newKeySet()
    @Volatile private var lastSweep: Instant = Instant.EPOCH

    fun bind(sessionId: String, userId: Long) {
        val now = Instant.now(clock)
        sweep(now)
        bindings[sessionId] = Binding(userId, now)
    }

    /** Whether [userId] may use [sessionId]; marks the session as used when it may. */
    fun allows(sessionId: String, userId: Long): Boolean {
        val now = Instant.now(clock)
        sweep(now)
        val binding = bindings[sessionId] ?: return false
        if (binding.userId != userId) return false
        binding.lastSeen = now
        return true
    }

    fun unbind(sessionId: String) {
        bindings.remove(sessionId)
    }

    /** Remembers an open SSE stream until the container is done with it. */
    fun track(tokenId: Long, sessionId: String, context: AsyncContext) {
        val stream = Stream(tokenId, sessionId, context)
        streams.add(stream)
        context.addListener(object : AsyncListener {
            override fun onComplete(event: AsyncEvent) { streams.remove(stream) }
            override fun onTimeout(event: AsyncEvent) { streams.remove(stream) }
            override fun onError(event: AsyncEvent) { streams.remove(stream) }
            override fun onStartAsync(event: AsyncEvent) {}
        })
    }

    // After the commit, so a client reconnecting at once already meets the revocation.
    @TransactionalEventListener(fallbackExecution = true)
    fun onTokenRevoked(event: TokenRevokedEvent) = close { it.tokenId == event.tokenId }

    @TransactionalEventListener(fallbackExecution = true)
    fun onMcpDisabled(event: McpDisabledEvent) = close { true }

    private fun close(which: (Stream) -> Boolean) {
        streams.filter(which).forEach { stream ->
            streams.remove(stream)
            try {
                stream.context.complete()
            } catch (e: IllegalStateException) {
                log.debug("MCP stream for session {} had already ended", stream.sessionId)
            }
        }
    }

    private fun sweep(now: Instant) {
        if (lastSweep.plus(SWEEP).isAfter(now)) return
        lastSweep = now
        val streaming = streams.mapTo(HashSet()) { it.sessionId }
        bindings.entries.removeIf { (id, binding) -> id !in streaming && !binding.lastSeen.plus(IDLE).isAfter(now) }
    }

    /** How many SSE streams are open right now. */
    fun openStreams(): Int = streams.size
}
