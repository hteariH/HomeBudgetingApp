package com.homebudget.app.repository

import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {
    fun findByAccountId(accountId: Long, pageable: Pageable): Page<Transaction>
    fun findByCategoryId(categoryId: Long, pageable: Pageable): Page<Transaction>
    fun findByType(type: TransactionType, pageable: Pageable): Page<Transaction>

    @Query("""
        SELECT t FROM Transaction t
        WHERE (:accountId IS NULL OR t.account.id = :accountId)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:type IS NULL OR t.type = :type)
          AND (:dateFrom IS NULL OR t.date >= :dateFrom)
          AND (:dateTo IS NULL OR t.date <= :dateTo)
        ORDER BY t.date DESC, t.createdAt DESC
    """)
    fun findFiltered(
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        dateFrom: LocalDate?,
        dateTo: LocalDate?,
        pageable: Pageable
    ): Page<Transaction>

    fun findByAccountIdOrderByDateDescCreatedAtDesc(accountId: Long): List<Transaction>
}
