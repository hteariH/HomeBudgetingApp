package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.CategoryDao
import com.personalmoney.core.database.dao.CategoryUseRow
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.domain.CategoryType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao
) {
    fun observeByType(type: CategoryType): Flow<List<CategoryEntity>> = dao.observeByType(type)
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    fun observeMostUsed(type: CategoryType, limit: Int = 8): Flow<List<CategoryUseRow>> =
        dao.observeMostUsed(type, limit)

    suspend fun findById(id: Long): CategoryEntity? = dao.findById(id)
    suspend fun create(category: CategoryEntity): Long = dao.insert(category)
    suspend fun update(category: CategoryEntity) = dao.update(category)
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun usageCount(id: Long): Int = dao.usageCount(id)
}
