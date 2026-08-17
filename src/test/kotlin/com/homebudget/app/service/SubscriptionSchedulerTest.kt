package com.homebudget.app.service

import com.homebudget.app.domain.SubscriptionFrequency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SubscriptionSchedulerTest {

    @Test
    fun `steps by each frequency`() {
        val start = LocalDate.of(2026, 3, 15)
        assertEquals(LocalDate.of(2026, 3, 16), SubscriptionScheduler.next(start, SubscriptionFrequency.DAILY, 1))
        assertEquals(LocalDate.of(2026, 3, 29), SubscriptionScheduler.next(start, SubscriptionFrequency.WEEKLY, 2))
        assertEquals(LocalDate.of(2026, 6, 15), SubscriptionScheduler.next(start, SubscriptionFrequency.MONTHLY, 3))
        assertEquals(LocalDate.of(2027, 3, 15), SubscriptionScheduler.next(start, SubscriptionFrequency.YEARLY, 1))
    }

    @Test
    fun `a charge on the 31st clamps into shorter months instead of skipping them`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb = SubscriptionScheduler.next(jan31, SubscriptionFrequency.MONTHLY, 1)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
    }

    @Test
    fun `catches up every missed period, not just the latest`() {
        // The point of the loop: a server down for four months still owes four charges.
        val result = SubscriptionScheduler.catchUp(
            nextChargeDate = LocalDate.of(2026, 1, 10),
            today = LocalDate.of(2026, 4, 15),
            frequency = SubscriptionFrequency.MONTHLY,
            intervalCount = 1,
            endDate = null
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 4, 10)
            ),
            result.dueDates
        )
        assertEquals(LocalDate.of(2026, 5, 10), result.nextChargeDate)
        assertFalse(result.finished)
    }

    @Test
    fun `produces nothing when the next charge is still in the future`() {
        val result = SubscriptionScheduler.catchUp(
            nextChargeDate = LocalDate.of(2026, 5, 1),
            today = LocalDate.of(2026, 4, 15),
            frequency = SubscriptionFrequency.MONTHLY,
            intervalCount = 1,
            endDate = null
        )
        assertTrue(result.dueDates.isEmpty())
        assertEquals(LocalDate.of(2026, 5, 1), result.nextChargeDate)
    }

    @Test
    fun `stops at the end date and reports itself finished`() {
        val result = SubscriptionScheduler.catchUp(
            nextChargeDate = LocalDate.of(2026, 1, 10),
            today = LocalDate.of(2026, 6, 1),
            frequency = SubscriptionFrequency.MONTHLY,
            intervalCount = 1,
            endDate = LocalDate.of(2026, 3, 31)
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10)),
            result.dueDates
        )
        assertTrue(result.finished, "a subscription past its end date should deactivate")
    }
}
