package com.homebudget.app.mcp

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The two limits on `/mcp`, each counted in a fixed one-minute window:
 *
 * - [LIMIT] tool calls per token;
 * - [FAILED_AUTH_LIMIT] rejected tokens per client address, beyond which further rejected
 *   tokens get 429 instead of 401 (valid tokens are never refused), so `/mcp` is no cheaper
 *   to guess tokens at than the sign-in form is to guess passwords at (see `AuthRateLimitFilter`, including why the
 *   address is the socket's and never a forwarding header).
 *
 * In memory, which is right for the single-container deployment; windows older than two
 * minutes are swept out.
 */
@Component
class McpRateLimiter(private val clock: Clock) {
    companion object {
        const val LIMIT = 60
        const val FAILED_AUTH_LIMIT = 30
        private val WINDOW: Duration = Duration.ofMinutes(1)
        private val KEEP: Duration = Duration.ofMinutes(2)
    }

    private val toolCalls = Windows<Long>()
    private val failedAuth = Windows<String>()

    /** Counts [calls] tool calls against [tokenId]; false when that goes over the limit. */
    fun tryAcquire(tokenId: Long, calls: Int = 1): Boolean = toolCalls.add(tokenId, calls) <= LIMIT

    /** Whether [address] has used up its failed authentications for this window. */
    fun authBlocked(address: String): Boolean = failedAuth.count(address) >= FAILED_AUTH_LIMIT

    fun recordAuthFailure(address: String) {
        failedAuth.add(address, 1)
    }

    /** How many tokens are being counted right now. */
    internal fun trackedTokens(): Int = toolCalls.size()

    private inner class Windows<K : Any> {
        private inner class Window(val startedAt: Instant, val count: Int) {
            fun live(now: Instant) = startedAt.plus(WINDOW).isAfter(now)
        }

        private val windows = ConcurrentHashMap<K, Window>()
        @Volatile private var lastSweep: Instant = Instant.EPOCH

        fun add(key: K, amount: Int): Int {
            val now = Instant.now(clock)
            sweep(now)
            return windows.compute(key) { _, existing ->
                if (existing == null || !existing.live(now)) Window(now, amount)
                else Window(existing.startedAt, existing.count + amount)
            }!!.count
        }

        fun count(key: K): Int {
            val now = Instant.now(clock)
            return windows[key]?.takeIf { it.live(now) }?.count ?: 0
        }

        fun size(): Int = windows.size

        private fun sweep(now: Instant) {
            if (lastSweep.plus(WINDOW).isAfter(now)) return
            lastSweep = now
            windows.values.removeIf { !it.startedAt.plus(KEEP).isAfter(now) }
        }
    }
}
