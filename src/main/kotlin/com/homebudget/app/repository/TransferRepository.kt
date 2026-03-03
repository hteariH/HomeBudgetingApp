package com.homebudget.app.repository

import com.homebudget.app.domain.Transfer
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransferRepository : JpaRepository<Transfer, Long> {
    fun findByFromAccountIdOrToAccountIdOrderByDateDescCreatedAtDesc(
        fromAccountId: Long,
        toAccountId: Long
    ): List<Transfer>

    fun findAllByOrderByDateDescCreatedAtDesc(pageable: Pageable): Page<Transfer>
}
