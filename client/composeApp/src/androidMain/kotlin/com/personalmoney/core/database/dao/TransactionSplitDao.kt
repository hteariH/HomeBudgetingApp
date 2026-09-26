package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.TransactionSplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionSplitDao {
    @Query("SELECT * FROM transaction_splits WHERE transaction_id = :transactionId AND deleted = 0 ORDER BY sort_order ASC, id ASC")
    fun observeByTransactionId(transactionId: Long): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_splits WHERE transaction_id = :transactionId AND deleted = 0 ORDER BY sort_order ASC, id ASC")
    suspend fun findByTransactionId(transactionId: Long): List<TransactionSplitEntity>

    @Query("SELECT * FROM transaction_splits WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun findById(id: Long): TransactionSplitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(split: TransactionSplitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(splits: List<TransactionSplitEntity>): List<Long>

    @Update
    suspend fun update(split: TransactionSplitEntity)

    @Query("UPDATE transaction_splits SET deleted = 1, pending_sync = 1 WHERE id = :id")
    suspend fun tombstoneById(id: Long)

    @Query("UPDATE transaction_splits SET deleted = 1, pending_sync = 1 WHERE transaction_id = :transactionId")
    suspend fun tombstoneByTransactionId(transactionId: Long)

    @Query("DELETE FROM transaction_splits WHERE transaction_id = :transactionId")
    suspend fun deleteByTransactionId(transactionId: Long)

    @Query("DELETE FROM transaction_splits")
    suspend fun deleteAll()

    // ── Sync ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM transaction_splits WHERE pending_sync = 1 ORDER BY id LIMIT :limit")
    suspend fun pendingForSync(limit: Int): List<TransactionSplitEntity>

    @Query("SELECT * FROM transaction_splits WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): TransactionSplitEntity?

    @Query("UPDATE transaction_splits SET pending_sync = 0, updated_at = :serverStamp WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String, serverStamp: Long)

    @Query("SELECT COUNT(*) FROM transaction_splits WHERE pending_sync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM transaction_splits WHERE deleted = 1 AND pending_sync = 0")
    suspend fun purgeSyncedTombstones(): Int
}
