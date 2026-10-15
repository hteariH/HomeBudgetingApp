package com.homebudget.app.receipt

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ReceiptRepository : JpaRepository<Receipt, Long> {

    fun findByUuidAndHouseholdId(uuid: String, householdId: Long): Receipt?

    // Transactions holding a receipt. Tombstones count: the unique index on
    // `transactions.receipt_id` covers them too. Two queries rather than one with
    // `:uuid IS NULL`, which PostgreSQL cannot type when the parameter is null.

    @Query(value = "SELECT COUNT(*) FROM transactions WHERE receipt_id = :receiptId", nativeQuery = true)
    fun countLinks(receiptId: Long): Long

    @Query(
        value = "SELECT COUNT(*) FROM transactions WHERE receipt_id = :receiptId AND uuid <> :transactionUuid",
        nativeQuery = true
    )
    fun countLinksExcept(receiptId: Long, transactionUuid: String): Long

    /**
     * Receipts older than [cutoff] that no live transaction holds: never saved with one,
     * or held only by a tombstone (an account deletion tombstones its rows in bulk).
     */
    @Query(
        value = """SELECT r.* FROM receipts r WHERE r.created_at < :cutoff AND NOT EXISTS
                   (SELECT 1 FROM transactions t WHERE t.receipt_id = r.id AND t.deleted = false)""",
        nativeQuery = true
    )
    fun findUnlinkedBefore(cutoff: Instant): List<Receipt>
}
