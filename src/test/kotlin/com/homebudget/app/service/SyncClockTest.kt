package com.homebudget.app.service

import com.homebudget.app.repository.SyncCursorRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Stamps are stored to the millisecond, so they are made to the millisecond. */
class SyncClockTest {

    private val now = Instant.parse("2026-04-01T10:00:00.123456789Z")
    private val latest = mutableMapOf<Long, Instant>()

    /** The stored stamps, without a database. (Mockito cannot mock this class on this JVM.) */
    private val cursors = object : SyncCursorRepository(
        java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(jakarta.persistence.EntityManager::class.java)) { _, _, _ ->
            throw UnsupportedOperationException()
        } as jakarta.persistence.EntityManager
    ) {
        override fun latestUpdatedAt(householdId: Long): Instant? = latest[householdId]
    }
    private val clock = SyncClock(cursors, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `with nothing stored the stamp is now, truncated to the millisecond`() {
        assertEquals(Instant.parse("2026-04-01T10:00:00.123Z"), clock.next(1))
    }

    @Test
    fun `a write in the same millisecond as the latest stamp goes one millisecond after it`() {
        // Unrounded, now (….123456789) is after ….123 - and is stored as ….123 again.
        latest[1] = Instant.parse("2026-04-01T10:00:00.123Z")
        assertEquals(Instant.parse("2026-04-01T10:00:00.124Z"), clock.next(1))
    }

    @Test
    fun `a latest stamp ahead of the clock is passed by one millisecond`() {
        latest[1] = Instant.parse("2026-04-01T10:00:05.000Z")
        latest[2] = Instant.parse("2026-04-01T10:00:07.000Z")
        assertEquals(Instant.parse("2026-04-01T10:00:07.001Z"), clock.next(1, 2))
    }
}
