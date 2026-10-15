package com.homebudget.app.repository

import com.homebudget.app.domain.ExchangeRate
import com.homebudget.app.domain.Household
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface ExchangeRateRepository : JpaRepository<ExchangeRate, Long> {

    fun findByBaseCodeAndQuoteCodeAndRateDateAndHouseholdIsNull(
        baseCode: String,
        quoteCode: String,
        rateDate: LocalDate
    ): ExchangeRate?

    /** A household's own rate for one pair. At most one row is kept; see `FxService.setOverride`. */
    fun findByHouseholdAndBaseCodeAndQuoteCode(household: Household, baseCode: String, quoteCode: String): List<ExchangeRate>

    /**
     * The newest global rate and the newest override for every pair the household can see.
     *
     * Newest *within its own scope*, which is the whole point of the correlated subquery
     * looking at `r.household`: with one shared MAX the nightly job's fresh global row hid an
     * override typed in a week earlier, and the override was never even returned for
     * [preferringOverrides] to prefer. Collapsing the two is that function's job.
     */
    @Query(
        """
        SELECT r FROM ExchangeRate r
        WHERE (r.household IS NULL OR r.household = :household)
          AND r.rateDate = (
              SELECT MAX(r2.rateDate) FROM ExchangeRate r2
              WHERE r2.baseCode = r.baseCode AND r2.quoteCode = r.quoteCode
                AND (
                     (r.household IS NULL AND r2.household IS NULL)
                     OR (r.household IS NOT NULL AND r2.household = :household)
                )
          )
        ORDER BY r.baseCode, r.quoteCode
        """
    )
    fun latestFor(household: Household): List<ExchangeRate>
}

/**
 * One row per pair, the household's own rate winning wherever it set one.
 *
 * [ExchangeRateRepository.latestFor] picks by date, and a date cannot express this: the
 * nightly job stamps today on every global rate, so a rate somebody typed in last week
 * lost to it, and one typed in today came back alongside it and won or lost depending on
 * which row the map happened to keep. A hand-set rate is a decision, not a stale reading.
 */
fun List<ExchangeRate>.preferringOverrides(): List<ExchangeRate> =
    groupBy { it.baseCode to it.quoteCode }
        .map { (_, rows) -> rows.firstOrNull { it.household != null } ?: rows.first() }
