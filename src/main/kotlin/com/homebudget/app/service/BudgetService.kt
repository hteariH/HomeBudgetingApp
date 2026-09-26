package com.homebudget.app.service

import com.homebudget.app.domain.Category
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth

@Service
class BudgetService {

    /**
     * Computes the date window [startDate, endDate] (inclusive) for a budget cycle.
     * When [startDay] is 1, it represents the standard calendar month.
     * When [startDay] > 1 (e.g. 10th of month), the cycle for year/month runs from
     * the [startDay] of the previous month to the day before [startDay] in the current month.
     */
    fun budgetCycleWindow(year: Int, month: Int, startDay: Int): Pair<LocalDate, LocalDate> {
        val clampedStartDay = startDay.coerceIn(1, 28)
        if (clampedStartDay == 1) {
            val ym = YearMonth.of(year, month)
            return ym.atDay(1) to ym.atEndOfMonth()
        }

        val currentMonthDate = LocalDate.of(year, month, clampedStartDay)
        val startDate = currentMonthDate.minusMonths(1)
        val endDate = currentMonthDate.minusDays(1)
        return startDate to endDate
    }

    /**
     * Calculates the effective available budget limit in minor units for a category,
     * applying rollover rules based on the previous period's unspent amount.
     *
     * @param baseLimitMinor Base monthly budget limit (or null if none)
     * @param previousPeriodUnspentMinor Positive for surplus (under budget), negative for deficit (over budget)
     * @param rolloverEnabled Whether unspent positive surplus carries forward
     * @param rolloverNegative Whether overspending deficit carries forward
     */
    fun calculateAvailableBudget(
        baseLimitMinor: Long?,
        previousPeriodUnspentMinor: Long,
        rolloverEnabled: Boolean,
        rolloverNegative: Boolean
    ): Long? {
        if (baseLimitMinor == null) return null

        var rolloverDelta = 0L
        if (previousPeriodUnspentMinor > 0L && rolloverEnabled) {
            rolloverDelta += previousPeriodUnspentMinor
        } else if (previousPeriodUnspentMinor < 0L && rolloverNegative) {
            rolloverDelta += previousPeriodUnspentMinor
        }

        return (baseLimitMinor + rolloverDelta).coerceAtLeast(0L)
    }

    /** Overload taking Category directly */
    fun calculateAvailableBudget(
        category: Category,
        previousPeriodUnspentMinor: Long
    ): Long? = calculateAvailableBudget(
        category.budgetLimitMinor,
        previousPeriodUnspentMinor,
        category.rolloverEnabled,
        category.rolloverNegative
    )
}
