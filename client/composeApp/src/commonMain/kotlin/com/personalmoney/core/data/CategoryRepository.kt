package com.personalmoney.core.data

import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.model.Category
import kotlinx.coroutines.flow.Flow

/** Categories, wherever they live. See [AccountRepository] for the guarantees. */
interface CategoryRepository {

    fun observeByType(type: CategoryType): Flow<List<Category>>

    fun observeAll(): Flow<List<Category>>

    /**
     * The categories the user reaches for, most-used first, for the quick-add chips.
     *
     * Ordered by how many ledger rows point at each, then by name so the order is stable
     * for a user who has entered nothing yet.
     */
    fun observeMostUsed(type: CategoryType, limit: Int = 8): Flow<List<Category>>

    suspend fun findById(id: Long): Category?

    suspend fun create(category: Category): Long

    suspend fun update(category: Category)

    /**
     * How many ledger rows still point at this category.
     *
     * Callers use it to choose between archiving and deleting: a category with history
     * cannot simply go, because the transactions that reference it would lose their label.
     */
    suspend fun usageCount(id: Long): Int

    suspend fun setArchived(id: Long, archived: Boolean)

    suspend fun delete(id: Long)

    suspend fun refresh() {}
}
