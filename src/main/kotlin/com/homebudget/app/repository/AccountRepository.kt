package com.homebudget.app.repository

import com.homebudget.app.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountRepository : JpaRepository<Account, Long> {
    fun findByNameContainingIgnoreCase(name: String): List<Account>
}
