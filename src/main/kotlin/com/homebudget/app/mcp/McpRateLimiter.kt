package com.homebudget.app.mcp

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * At most [LIMIT] tool calls per token in a fixed one-minute window. In memory, which is
 * right for the single-container deployment; windows older than two minutes are swept out.
 */
@Component
class McpRateLimiter(private val clock: Clock) {
    companion object {
        const val LIMIT = 60
        private val WINDOW: Duration = Duration.ofMinutes(1)
        private val KEEP: Duration = Duration.ofMinutes(2)
    }

    private data class Window(val startedAt: Instant, val count: Int)

    private val windows = ConcurrentHashMap<Long, Window>()
    @Volatile private var lastSweep: Instant = Instant.EPOCH

    /** Counts [calls] tool calls against [tokenId]; false when that goes over the limit. */
    fun tryAcquire(tokenId: Long, calls: Int = 1): Boolean {
        val now = Instant.now(clock)
        sweep(now)
        val window = windows.compute(tokenId) { _, existing ->
            if (existing == null || !existing.startedAt.plus(WINDOW).isAfter(now)) Window(now, calls)
            else existing.copy(count = existing.count + calls)
        }!!
        return window.count <= LIMIT
    }

    private fun sweep(now: Instant) {
        if (lastSweep.plus(WINDOW).isAfter(now)) return
        lastSweep = now
        windows.values.removeIf { !it.startedAt.plus(KEEP).isAfter(now) }
    }

    /** How many tokens are being counted right now. */
    internal fun trackedTokens(): Int = windows.size
}
