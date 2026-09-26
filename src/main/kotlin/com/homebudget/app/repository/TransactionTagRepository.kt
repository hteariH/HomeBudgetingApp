package com.homebudget.app.repository

import com.homebudget.app.domain.TransactionTag
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface TransactionTagRepository : JpaRepository<TransactionTag, Long> {
    fun findByTransactionId(transactionId: Long): List<TransactionTag>
    fun findByTagId(tagId: Long): List<TransactionTag>
    fun findByIdAndUser(id: Long, user: User): TransactionTag?
    fun findByUuidAndUser(uuid: String, user: User): TransactionTag?
    fun findByTransactionIdAndTagId(transactionId: Long, tagId: Long): TransactionTag?

    @Query(
        value = "SELECT * FROM transaction_tags WHERE user_id = :userId AND updated_at > :since ORDER BY updated_at, id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(userId: Long, since: Instant, limit: Int): List<TransactionTag>

    @Query(value = "SELECT * FROM transaction_tags WHERE uuid = :uuid AND user_id = :userId", nativeQuery = true)
    fun findByUuidIncludingDeleted(uuid: String, userId: Long): TransactionTag?
}
