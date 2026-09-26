package com.homebudget.app.repository

import com.homebudget.app.domain.TransactionSplit
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface TransactionSplitRepository : JpaRepository<TransactionSplit, Long> {
    fun findByTransactionId(transactionId: Long): List<TransactionSplit>
    fun findByTransactionUuid(transactionUuid: String): List<TransactionSplit>
    fun findByIdAndUser(id: Long, user: User): TransactionSplit?
    fun findByUuidAndUser(uuid: String, user: User): TransactionSplit?
    fun countByCategoryId(categoryId: Long): Long

    @Query(
        value = "SELECT * FROM transaction_splits WHERE user_id = :userId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(userId: Long, since: Instant, limit: Int): List<TransactionSplit>

    @Query(value = "SELECT * FROM transaction_splits WHERE uuid = :uuid AND user_id = :userId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, userId: Long): TransactionSplit?

    @Query(value = "SELECT * FROM transaction_splits WHERE transaction_id = :transactionId", nativeQuery = true)
    fun findAllByTransactionIdIncludingDeleted(transactionId: Long): List<TransactionSplit>
}
