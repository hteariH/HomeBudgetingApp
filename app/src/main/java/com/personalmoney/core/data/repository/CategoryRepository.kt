package com.personalmoney.core.data.repository

import com.personalmoney.core.database.Transactor
import com.personalmoney.core.database.dao.CategoryDao
import com.personalmoney.core.database.dao.CategoryUseRow
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Public surface unchanged; writes now mark rows for sync and deletes leave tombstones. */
@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao,
    private val transactor: Transactor,
    private val syncTrigger: SyncTrigger
) {
    fun observeByType(type: CategoryType): Flow<List<CategoryEntity>> = dao.observeByType(type)
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    fun observeMostUsed(type: CategoryType, limit: Int = 8): Flow<List<CategoryUseRow>> =
        dao.observeMostUsed(type, limit)

    suspend fun findById(id: Long): CategoryEntity? = dao.findById(id)

    suspend fun create(category: CategoryEntity): Long =
        dao.insert(category.copy(pendingSync = true)).also { syncTrigger.requestSync() }

    suspend fun update(category: CategoryEntity) {
        dao.update(category.copy(pendingSync = true))
        syncTrigger.requestSync()
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        dao.setArchived(id, archived)
        syncTrigger.requestSync()
    }

    /** Unlinks history rather than deleting it along with the category. */
    suspend fun delete(id: Long) {
        transactor.transaction {
            dao.detachTransactionsOf(id)
            dao.tombstoneById(id)
        }
        syncTrigger.requestSync()
    }

    suspend fun usageCount(id: Long): Int = dao.usageCount(id)
}
