package com.homebudget.app.repository

import com.homebudget.app.domain.BankConnection
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BankConnectionRepository : JpaRepository<BankConnection, Long> {
    fun findAllByUser(user: User): List<BankConnection>
    fun findByIdAndUser(id: Long, user: User): BankConnection?
}
