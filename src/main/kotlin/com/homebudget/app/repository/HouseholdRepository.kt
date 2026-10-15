package com.homebudget.app.repository

import com.homebudget.app.domain.Household
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface HouseholdRepository : JpaRepository<Household, Long> {

    /** The currencies totals are reported in, which is half of what the FX job quotes. */
    @Query("SELECT DISTINCT h.baseCurrency.code FROM Household h WHERE h.baseCurrency IS NOT NULL")
    fun baseCurrencyCodes(): List<String>
}
