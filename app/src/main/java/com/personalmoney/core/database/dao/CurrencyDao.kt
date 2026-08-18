package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow

/**
 * The catalog itself is reference data the server owns; what syncs is which entries a
 * user has switched on and in what order, which is why these rows carry sync metadata
 * too.
 */
@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE deleted = 0 AND active = 1 ORDER BY sortOrder, code")
    fun observeActive(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE deleted = 0 ORDER BY active DESC, sortOrder, code")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE code = :code AND deleted = 0 LIMIT 1")
    suspend fun findByCode(code: String): CurrencyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(currency: CurrencyEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(currencies: List<CurrencyEntity>): List<Long>

    @Update
    suspend fun update(currency: CurrencyEntity)

    @Query("UPDATE currencies SET active = :active, pending_sync = 1 WHERE code = :code")
    suspend fun setActive(code: String, active: Boolean)

    @Query("UPDATE currencies SET deleted = 1, pending_sync = 1 WHERE code = :code")
    suspend fun tombstoneByCode(code: String)

    /** Only for a full local reset, e.g. signing out. */
    @Query("DELETE FROM currencies")
    suspend fun deleteAll()

    @Query(
        """
        SELECT (
            (SELECT COUNT(*) FROM accounts WHERE currency_code = :code AND deleted = 0) +
            (SELECT COUNT(*) FROM transactions WHERE currency_code = :code AND deleted = 0) +
            (SELECT COUNT(*) FROM subscriptions WHERE currency_code = :code AND deleted = 0)
        )
        """
    )
    suspend fun usageCount(code: String): Int

    // ── Sync ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM currencies WHERE pending_sync = 1 ORDER BY code LIMIT :limit")
    suspend fun pendingForSync(limit: Int): List<CurrencyEntity>

    @Query("SELECT * FROM currencies WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): CurrencyEntity?

    @Query("UPDATE currencies SET pending_sync = 0, updated_at = :serverStamp WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String, serverStamp: Long)

    @Query("SELECT COUNT(*) FROM currencies WHERE pending_sync = 1")
    fun observePendingCount(): Flow<Int>
}
