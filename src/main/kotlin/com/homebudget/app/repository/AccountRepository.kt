package com.homebudget.app.repository

import com.homebudget.app.domain.Account
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountRepository : JpaRepository<Account, Long> {
    fun findAllByUser(user: User): List<Account>
    fun findByIdAndUser(id: Long, user: User): Account?
}
