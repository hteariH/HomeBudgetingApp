package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/** Every read filters `deleted = 0`; see the note on [TransactionDao]. */
@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE deleted = 0 ORDER BY active DESC, next_charge_date ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE deleted = 0 AND active = 1 ORDER BY next_charge_date ASC")
    fun observeActive(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun findById(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE deleted = 0 AND active = 1 AND next_charge_date <= :asOfDate")
    suspend fun findDueAsOf(asOfDate: String): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET active = :active, pending_sync = 1 WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE subscriptions SET next_charge_date = :next, pending_sync = 1 WHERE id = :id")
    suspend fun setNextChargeDate(id: Long, next: String)

    @Query("UPDATE subscriptions SET deleted = 1, pending_sync = 1 WHERE id = :id")
    suspend fun tombstoneById(id: Long)

    /** Only for a full local reset, e.g. signing out. */
    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()

    // ── Sync ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM subscriptions WHERE pending_sync = 1 ORDER BY id LIMIT :limit")
    suspend fun pendingForSync(limit: Int): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): SubscriptionEntity?

    @Query("UPDATE subscriptions SET pending_sync = 0, updated_at = :serverStamp WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String, serverStamp: Long)

    @Query("SELECT COUNT(*) FROM subscriptions WHERE pending_sync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM subscriptions WHERE deleted = 1 AND pending_sync = 0")
    suspend fun purgeSyncedTombstones(): Int
}
