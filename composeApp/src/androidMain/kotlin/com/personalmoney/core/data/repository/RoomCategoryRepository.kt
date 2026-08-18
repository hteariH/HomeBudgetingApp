package com.personalmoney.core.data.repository

import com.personalmoney.core.data.CategoryRepository as SharedCategoryRepository
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The shared category contract, backed by Room. */
class RoomCategoryRepository(
    private val delegate: CategoryRepository,
) : SharedCategoryRepository {

    override fun observeByType(type: CategoryType): Flow<List<Category>> =
        delegate.observeByType(type).map { rows -> rows.map { it.toCategory() } }

    override fun observeAll(): Flow<List<Category>> =
        delegate.observeAll().map { rows -> rows.map { it.toCategory() } }

    override suspend fun findById(id: Long): Category? = delegate.findById(id)?.toCategory()

    override suspend fun create(category: Category): Long = delegate.create(category.toEntity())

    override suspend fun update(category: Category) = delegate.update(category.toEntity())

    override suspend fun usageCount(id: Long): Int = delegate.usageCount(id)

    override suspend fun setArchived(id: Long, archived: Boolean) =
        delegate.setArchived(id, archived)

    override suspend fun delete(id: Long) = delegate.delete(id)
}

private fun CategoryEntity.toCategory() = Category(
    id = id,
    uuid = uuid,
    name = name,
    type = type,
    icon = icon,
    color = color,
    parentId = parentId,
    archived = archived,
    sortOrder = sortOrder,
    isDefault = isDefault,
    budgetLimitMinor = budgetLimitMinor,
)

private fun Category.toEntity() = CategoryEntity(
    id = id,
    name = name,
    type = type,
    icon = icon,
    color = color,
    parentId = parentId,
    archived = archived,
    sortOrder = sortOrder,
    isDefault = isDefault,
    budgetLimitMinor = budgetLimitMinor,
    uuid = uuid,
)
