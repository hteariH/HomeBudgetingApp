package com.personalmoney.feature.subscriptions

import com.personalmoney.core.domain.SubscriptionFrequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object SubscriptionScheduler {
    fun computeNextChargeDate(
        current: LocalDate,
        frequency: SubscriptionFrequency,
        interval: Int
    ): LocalDate {
        val n = interval.coerceAtLeast(1)
        return when (frequency) {
            SubscriptionFrequency.DAILY -> current.plus(n, DateTimeUnit.DAY)
            SubscriptionFrequency.WEEKLY -> current.plus(n * 7, DateTimeUnit.DAY)
            SubscriptionFrequency.MONTHLY -> current.plus(n, DateTimeUnit.MONTH)
            SubscriptionFrequency.YEARLY -> current.plus(n, DateTimeUnit.YEAR)
        }
    }
}
