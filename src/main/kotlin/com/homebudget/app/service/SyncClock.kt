package com.homebudget.app.service

import com.homebudget.app.repository.SyncCursorRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

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
        // The columns keep milliseconds. A finer instant compares as later than a stamp in
        // the same millisecond, then is stored as that very stamp.
        val now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
        val latest = householdIds.asList().mapNotNull { cursors.latestUpdatedAt(it) }.maxOrNull() ?: return now
        return if (now.isAfter(latest)) now else latest.plusMillis(1)
    }
}
