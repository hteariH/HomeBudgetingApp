package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.domain.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY sort_order, name")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE archived = 0 AND type = :type ORDER BY sort_order, name")
    fun observeByType(type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY archived, type, sort_order, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :id")
    suspend fun usageCount(id: Long): Int

    @Query(
        """
        SELECT c.id AS categoryId, COUNT(t.id) AS uses
        FROM categories c LEFT JOIN transactions t ON t.category_id = c.id
        WHERE c.type = :type AND c.archived = 0
        GROUP BY c.id
        ORDER BY uses DESC, c.name
        LIMIT :limit
        """
    )
    fun observeMostUsed(type: CategoryType, limit: Int = 8): Flow<List<CategoryUseRow>>
}

data class CategoryUseRow(val categoryId: Long, val uses: Int)
