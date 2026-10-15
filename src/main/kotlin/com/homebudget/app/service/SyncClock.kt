package com.homebudget.app.service

import com.homebudget.app.repository.SyncCursorRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Stamps strictly later than everything already stored in the given households.
 *
 * Millisecond collisions are not hypothetical - creating and deleting a row in quick
 * succession lands both in the same millisecond, and `updated_at > cursor` then loses
 * the second one permanently.
 *
 * Callers hold those households' row locks (HouseholdRepository.lockById), so no other
 * writer can slip a stamp in between reading the latest and writing the next.
 */
@Component
class SyncClock(private val cursors: SyncCursorRepository, private val clock: Clock) {
    fun next(vararg householdIds: Long): Instant {
        val now = Instant.now(clock)
        val latest = householdIds.asList().mapNotNull { cursors.latestUpdatedAt(it) }.maxOrNull() ?: return now
        return if (now.isAfter(latest)) now else latest.plusMillis(1)
    }
}
