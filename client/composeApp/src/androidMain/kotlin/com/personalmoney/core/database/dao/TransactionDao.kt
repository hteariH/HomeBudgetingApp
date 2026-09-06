package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Every read filters `deleted = 0`.
 *
 * Deletes are tombstones now, so a row that has been removed is still physically there
 * until the server has been told. Room has no equivalent of a global filter, so the
 * condition is repeated in each query - a missing one shows deleted rows in the UI and,
 * worse, counts them in a balance.
 */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id AND deleted = 0 LIMIT 1")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted = 0
          AND (:fromMs IS NULL OR occurred_at >= :fromMs)
          AND (:toMs IS NULL OR occurred_at < :toMs)
          AND (:accountId IS NULL OR account_id = :accountId)
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:typeName IS NULL OR type = :typeName)
        ORDER BY occurred_at DESC, id DESC
        """
    )
    fun observeFiltered(
        fromMs: Long?,
        toMs: Long?,
        accountId: Long?,
        categoryId: Long?,
        typeName: String?
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transfer_group_id = :groupId AND deleted = 0")
    suspend fun findTransferPair(groupId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transactions SET deleted = 1, pending_sync = 1 WHERE id = :id")
    suspend fun tombstoneById(id: Long)

    @Query("UPDATE transactions SET deleted = 1, pending_sync = 1 WHERE transfer_group_id = :groupId")
    suspend fun tombstoneByTransferGroup(groupId: String)

    /** Only for a full local reset, e.g. signing out. Never as part of an edit. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Transaction
    suspend fun insertTransferPair(debit: TransactionEntity, credit: TransactionEntity): Pair<Long, Long> {
        val a = insert(debit)
        val b = insert(credit)
        return a to b
    }

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = :typeName THEN account_amount_minor ELSE 0 END), 0) AS totalMinor
        FROM transactions
        WHERE deleted = 0
          AND occurred_at BETWEEN :fromMs AND :toMs
          AND account_id = :accountId
        """
    )
    fun observeSumByTypeInAccount(typeName: String, accountId: Long, fromMs: Long, toMs: Long): Flow<Long>

    @Query(
        """
        SELECT category_id AS categoryId,
               COALESCE(SUM(account_amount_minor), 0) AS totalMinor,
               COUNT(*) AS uses
        FROM transactions
        WHERE deleted = 0
          AND type = :typeName
          AND occurred_at BETWEEN :fromMs AND :toMs
        GROUP BY category_id
        ORDER BY totalMinor DESC
        """
    )
    fun observeCategoryTotals(typeName: String, fromMs: Long, toMs: Long): Flow<List<CategoryTotalRow>>

    // ── Sync ────────────────────────────────────────────────────────────────
    // These deliberately ignore `deleted`: tombstones are exactly what has to be sent.

    @Query("SELECT * FROM transactions WHERE pending_sync = 1 ORDER BY id LIMIT :limit")
    suspend fun pendingForSync(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): TransactionEntity?

    @Query("UPDATE transactions SET pending_sync = 0, updated_at = :serverStamp WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String, serverStamp: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE pending_sync = 1")
    fun observePendingCount(): Flow<Int>

    /** Reaps tombstones the server has acknowledged; nothing else refers to them. */
    @Query("DELETE FROM transactions WHERE deleted = 1 AND pending_sync = 0")
    suspend fun purgeSyncedTombstones(): Int
}

data class CategoryTotalRow(
    val categoryId: Long?,
    val totalMinor: Long,
    val uses: Int
)
