package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.domain.CategoryType
import kotlinx.coroutines.flow.Flow

/** Every read filters `deleted = 0`; see the note on [TransactionDao]. */
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deleted = 0 AND archived = 0 ORDER BY sort_order, name")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0 AND archived = 0 AND type = :type ORDER BY sort_order, name")
    fun observeByType(type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY archived, type, sort_order, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun findById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET archived = :archived, pending_sync = 1 WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE categories SET deleted = 1, pending_sync = 1 WHERE id = :id")
    suspend fun tombstoneById(id: Long)

    /**
     * Unlinks the history instead of taking it with the category. category_id is
     * nullable precisely so that removing a category leaves the spending in place,
     * uncategorised.
     */
    @Query("UPDATE transactions SET category_id = NULL, pending_sync = 1 WHERE category_id = :id")
    suspend fun detachTransactionsOf(id: Long)

    /** Only for a full local reset, e.g. signing out. */
    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query(
        """
        SELECT (
            (SELECT COUNT(*) FROM transactions WHERE category_id = :id AND deleted = 0) +
            (SELECT COUNT(*) FROM subscriptions WHERE category_id = :id AND deleted = 0)
        )
        """
    )
    suspend fun usageCount(id: Long): Int

    @Query(
        """
        SELECT c.id AS categoryId, COUNT(t.id) AS uses
        FROM categories c LEFT JOIN transactions t ON t.category_id = c.id AND t.deleted = 0
        WHERE c.type = :type AND c.archived = 0 AND c.deleted = 0
        GROUP BY c.id
        ORDER BY uses DESC, c.name
        LIMIT :limit
        """
    )
    fun observeMostUsed(type: CategoryType, limit: Int = 8): Flow<List<CategoryUseRow>>

    // ── Sync ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM categories WHERE pending_sync = 1 ORDER BY id LIMIT :limit")
    suspend fun pendingForSync(limit: Int): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): CategoryEntity?

    @Query("UPDATE categories SET pending_sync = 0, updated_at = :serverStamp WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String, serverStamp: Long)

    @Query("SELECT COUNT(*) FROM categories WHERE pending_sync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM categories WHERE deleted = 1 AND pending_sync = 0")
    suspend fun purgeSyncedTombstones(): Int

    /**
     * Drops the app's own starter categories once the server has supplied its own.
     *
     * Scoped to rows that are default, never synced (updated_at = 0), never edited and
     * never used - so a user who renamed or spent against a default keeps it. Without
     * this a fresh install signing into an existing account shows both sets.
     */
    @Query(
        """
        DELETE FROM categories
        WHERE is_default = 1
          AND updated_at = 0
          AND pending_sync = 0
          AND id NOT IN (SELECT DISTINCT category_id FROM transactions WHERE category_id IS NOT NULL)
          AND id NOT IN (SELECT DISTINCT category_id FROM subscriptions WHERE category_id IS NOT NULL)
        """
    )
    suspend fun dropUnusedLocalDefaults(): Int
}

data class CategoryUseRow(val categoryId: Long, val uses: Int)
