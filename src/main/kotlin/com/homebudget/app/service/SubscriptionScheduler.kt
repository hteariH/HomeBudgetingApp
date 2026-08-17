package com.homebudget.app.service

import com.homebudget.app.domain.SubscriptionFrequency
import java.time.LocalDate

/**
 * Date arithmetic for recurring charges, kept pure so it can be tested without a
 * database or a clock.
 *
 * Ported from the Android client's `core/subscription/SubscriptionScheduler`, whose
 * behaviour is already covered by unit tests there; keeping the two identical is what
 * stops a subscription charging on different days on phone and web.
 */
object SubscriptionScheduler {

    /**
     * The charge date after [from], [intervalCount] periods later.
     *
     * Month and year steps clamp to the end of the target month, so a subscription
     * starting on the 31st charges on the 28th/30th in shorter months rather than
     * skipping them.
     */
    fun next(from: LocalDate, frequency: SubscriptionFrequency, intervalCount: Int): LocalDate {
        require(intervalCount > 0) { "interval must be positive, was $intervalCount" }
        return when (frequency) {
            SubscriptionFrequency.DAILY -> from.plusDays(intervalCount.toLong())
            SubscriptionFrequency.WEEKLY -> from.plusWeeks(intervalCount.toLong())
            SubscriptionFrequency.MONTHLY -> from.plusMonths(intervalCount.toLong())
            SubscriptionFrequency.YEARLY -> from.plusYears(intervalCount.toLong())
        }
    }

    /**
     * Every charge date from [nextChargeDate] up to and including [today], and the new
     * next-charge date to store.
     *
     * Catching up in a loop matters because nothing guarantees the job ran yesterday:
     * a server that was down for a week must still produce that week's charges.
     */
    fun catchUp(
        nextChargeDate: LocalDate,
        today: LocalDate,
        frequency: SubscriptionFrequency,
        intervalCount: Int,
        endDate: LocalDate?
    ): CatchUp {
        val due = mutableListOf<LocalDate>()
        var cursor = nextChargeDate
        // Bounded to keep a corrupt row from spinning forever.
        var guard = 0
        while (!cursor.isAfter(today) && guard++ < MAX_CATCH_UP) {
            if (endDate != null && cursor.isAfter(endDate)) break
            due += cursor
            cursor = next(cursor, frequency, intervalCount)
        }
        val finished = endDate != null && cursor.isAfter(endDate)
        return CatchUp(dueDates = due, nextChargeDate = cursor, finished = finished)
    }

    data class CatchUp(
        val dueDates: List<LocalDate>,
        val nextChargeDate: LocalDate,
        val finished: Boolean
    )

    private const val MAX_CATCH_UP = 1000
}
