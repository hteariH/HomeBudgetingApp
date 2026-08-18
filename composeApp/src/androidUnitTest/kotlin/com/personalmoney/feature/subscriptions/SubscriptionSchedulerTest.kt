package com.personalmoney.feature.subscriptions

import com.personalmoney.core.domain.SubscriptionFrequency
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionSchedulerTest {

    @Test fun `daily increment adds one day`() {
        val next = SubscriptionScheduler.computeNextChargeDate(
            LocalDate(2026, 5, 15), SubscriptionFrequency.DAILY, 1
        )
        assertEquals(LocalDate(2026, 5, 16), next)
    }

    @Test fun `weekly increment adds seven days`() {
        val next = SubscriptionScheduler.computeNextChargeDate(
            LocalDate(2026, 5, 1), SubscriptionFrequency.WEEKLY, 1
        )
        assertEquals(LocalDate(2026, 5, 8), next)
    }

    @Test fun `monthly increment preserves day`() {
        val next = SubscriptionScheduler.computeNextChargeDate(
            LocalDate(2026, 1, 31), SubscriptionFrequency.MONTHLY, 1
        )
        // Feb has 28 days in 2026, kotlinx-datetime clamps to last valid day
        assertEquals(LocalDate(2026, 2, 28), next)
    }

    @Test fun `yearly increment`() {
        val next = SubscriptionScheduler.computeNextChargeDate(
            LocalDate(2026, 5, 28), SubscriptionFrequency.YEARLY, 1
        )
        assertEquals(LocalDate(2027, 5, 28), next)
    }

    @Test fun `interval multiplies properly`() {
        val next = SubscriptionScheduler.computeNextChargeDate(
            LocalDate(2026, 1, 1), SubscriptionFrequency.WEEKLY, 2
        )
        assertEquals(LocalDate(2026, 1, 15), next)
    }
}
