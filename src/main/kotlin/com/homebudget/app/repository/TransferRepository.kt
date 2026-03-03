package com.homebudget.app.repository

import com.homebudget.app.domain.Transfer
import com.homebudget.app.domain.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransferRepository : JpaRepository<Transfer, Long> {
    fun findAllByUser(user: User, pageable: Pageable): Page<Transfer>
    fun findByIdAndUser(id: Long, user: User): Transfer?
}
