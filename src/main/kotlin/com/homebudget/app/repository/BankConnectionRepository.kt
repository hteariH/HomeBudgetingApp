package com.homebudget.app.repository

import com.homebudget.app.domain.BankConnection
import com.homebudget.app.domain.Household
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface BankConnectionRepository : JpaRepository<BankConnection, Long> {
    fun findAllByHousehold(household: Household): List<BankConnection>
    fun findByIdAndHousehold(id: Long, household: Household): BankConnection?

    /**
     * Deletes the connections [userId] made in [householdId]. Native, so the database's
     * `ON DELETE SET NULL` unlinks the accounts that imported through them.
     */
    @Modifying
    @Query(
        value = "DELETE FROM bank_connections WHERE household_id = :householdId AND connected_by_user_id = :userId",
        nativeQuery = true
    )
    fun deleteMadeBy(householdId: Long, userId: Long): Int
}
