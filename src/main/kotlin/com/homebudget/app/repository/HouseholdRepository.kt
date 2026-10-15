package com.homebudget.app.repository

import com.homebudget.app.domain.Household
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface HouseholdRepository : JpaRepository<Household, Long> {

    /** The currencies totals are reported in, which is half of what the FX job quotes. */
    @Query("SELECT DISTINCT h.baseCurrency.code FROM Household h WHERE h.baseCurrency IS NOT NULL")
    fun baseCurrencyCodes(): List<String>

    /**
     * Takes the household's row lock for the rest of the transaction.
     *
     * Sync pushes into one household go one at a time behind it, so each stamps strictly
     * after everything committed before it - see SyncService.nextStamp.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Household h WHERE h.id = :id")
    fun lockById(id: Long): Household?
}
