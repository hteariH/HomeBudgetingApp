package com.homebudget.app.repository

import com.homebudget.app.domain.ExchangeRate
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface ExchangeRateRepository : JpaRepository<ExchangeRate, Long> {

    fun findByBaseCodeAndQuoteCodeAndRateDateAndUserIsNull(
        baseCode: String,
        quoteCode: String,
        rateDate: LocalDate
    ): ExchangeRate?

    /**
     * Latest rate per pair: a user's own override where one exists, otherwise the
     * globally fetched figure.
     */
    @Query(
        """
        SELECT r FROM ExchangeRate r
        WHERE (r.user IS NULL OR r.user = :user)
          AND r.rateDate = (
              SELECT MAX(r2.rateDate) FROM ExchangeRate r2
              WHERE r2.baseCode = r.baseCode AND r2.quoteCode = r.quoteCode
                AND (r2.user IS NULL OR r2.user = :user)
          )
        ORDER BY r.baseCode, r.quoteCode
        """
    )
    fun latestFor(user: User): List<ExchangeRate>
}
