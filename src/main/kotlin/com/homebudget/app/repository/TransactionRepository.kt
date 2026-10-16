package com.homebudget.app.repository

import com.homebudget.app.domain.Account
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.domain.Household
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface TransactionRepository : JpaRepository<Transaction, Long> {
    fun findByIdAndHousehold(id: Long, household: Household): Transaction?
    fun findByUuidAndHousehold(uuid: String, household: Household): Transaction?
    fun existsByAccountAndExternalId(account: Account, externalId: String): Boolean
    fun findByAccountAndExternalId(account: Account, externalId: String): Transaction?
    fun countByCategoryId(categoryId: Long): Long

    /** Both legs of a transfer. Deleting one leg without the other corrupts balances. */
    fun findAllByTransferGroupIdAndHousehold(transferGroupId: String, household: Household): List<Transaction>

    @Query(
        """
        SELECT t FROM Transaction t
        WHERE t.household = :household
          AND (:accountId IS NULL OR t.account.id = :accountId)
          AND (:categoryId IS NULL OR t.category.id = :categoryId)
          AND (:type IS NULL OR t.type = :type)
          AND (:createdBy IS NULL OR t.createdBy.id = :createdBy)
          AND (:from IS NULL OR t.occurredAt >= :from)
          AND (:until IS NULL OR t.occurredAt < :until)
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun findFiltered(
        household: Household,
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        createdBy: Long?,
        from: Instant?,
        until: Instant?,
        pageable: Pageable
    ): Page<Transaction>

    /**
     * Tombstones every row on an account. FK cascades cannot do this: a soft delete is
     * an UPDATE, so the children have to be walked explicitly or they stay visible.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.deleted = true, t.updatedAt = :now WHERE t.account.id = :accountId AND t.deleted = false")
    fun tombstoneByAccount(accountId: Long, now: Instant): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.category = null, t.updatedAt = :now WHERE t.category.id = :categoryId AND t.deleted = false")
    fun detachCategory(categoryId: Long, now: Instant): Int

    /**
     * One row per transfer group, newest first - the debit leg, which is the one
     * carrying the fee and the negative amount.
     */
    @Query(
        """
        SELECT t FROM Transaction t
        WHERE t.household = :household
          AND t.type = com.homebudget.app.domain.TransactionType.TRANSFER
          AND t.accountAmountMinor < 0
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun findTransferDebitLegs(household: Household, pageable: Pageable): Page<Transaction>

    // ── Sync access ─────────────────────────────────────────────────────────
    // Native SQL on purpose: @SQLRestriction hides tombstones from every JPQL query,
    // and sync is the one place that must see them - a deletion the client never
    // learns about would be pushed straight back. Each also selects what the entity's
    // @Formula reads - the receipt's uuid - under the property's name.

    @Query(
        value = "SELECT t.*, $RECEIPT_UUID FROM transactions t WHERE t.household_id = :householdId AND t.updated_at > :since " +
            "ORDER BY t.updated_at, t.id LIMIT :limit",
        nativeQuery = true
    )
    fun findChangedSince(householdId: Long, since: Instant, limit: Int): List<Transaction>

    @Query(
        value = "SELECT t.*, $RECEIPT_UUID FROM transactions t WHERE t.uuid = :uuid AND t.household_id = :householdId",
        nativeQuery = true
    )
    fun findByUuidIncludingDeleted(uuid: String, householdId: Long): Transaction?

    companion object {
        const val RECEIPT_UUID = "(SELECT r.uuid FROM receipts r WHERE r.id = t.receipt_id) AS receiptUuid"
    }
}
