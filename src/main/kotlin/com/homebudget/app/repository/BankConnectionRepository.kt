package com.homebudget.app.repository

import com.homebudget.app.domain.BankConnection
import com.homebudget.app.domain.Household
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BankConnectionRepository : JpaRepository<BankConnection, Long> {
    fun findAllByHousehold(household: Household): List<BankConnection>
    fun findByIdAndHousehold(id: Long, household: Household): BankConnection?
}
