package com.personalmoney.core.data

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.toDecimalString
import com.personalmoney.core.domain.toMinorUnits
import com.personalmoney.core.network.CreateCategoryRequest
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The shared category contract, backed by REST. See [RestAccountRepository]. */
class RestCategoryRepository(private val api: RestApi) : CategoryRepository {

    private val cache = MutableStateFlow<List<Category>>(emptyList())

    override fun observeByType(type: CategoryType): Flow<List<Category>> =
        cache.map { all -> all.filter { it.type == type && !it.archived }.sortedBy { it.sortOrder } }

    override fun observeAll(): Flow<List<Category>> = cache

    override suspend fun findById(id: Long): Category? = cache.value.firstOrNull { it.id == id }

    override suspend fun create(category: Category): Long {
        val created = api.createCategory(category.toRequest())
        refresh()
        return created.id
    }

    override suspend fun update(category: Category) {
        api.updateCategory(category.id, category.toRequest())
        refresh()
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        // No archive field on the REST create/update body, so archiving is a delete on the
        // server side; it keeps the row and its history, exactly as the web client does.
        if (archived) api.deleteCategory(id)
        refresh()
    }

    override suspend fun delete(id: Long) {
        api.deleteCategory(id)
        refresh()
    }

    override suspend fun refresh() {
        cache.value = api.categories().map { it.toCategory() }
    }
}

private fun RestCategory.toCategory() = Category(
    id = id,
    uuid = uuid,
    name = name,
    type = type,
    icon = icon,
    color = color.parseColorOrDefault(),
    parentId = parentId,
    archived = archived,
    sortOrder = sortOrder,
    isDefault = isDefault,
    // Budget limits are in base-currency minor units and the server sends major units as a
    // string. Two decimals is the base-currency assumption the server itself makes here.
    budgetLimitMinor = budgetLimit?.let { BigDecimal.parseString(it).toMinorUnits(2) },
)

private fun Category.toRequest() = CreateCategoryRequest(
    name = name,
    type = type,
    icon = icon,
    color = color.toHexColor(),
    parentId = parentId,
    sortOrder = sortOrder,
    budgetLimit = budgetLimitMinor?.let { minor ->
        minor.toDecimalString(2)
    },
)

/** The server stores colours as "#RRGGBB"; the model carries the ARGB value Compose wants. */
private fun String.parseColorOrDefault(): Long {
    val hex = removePrefix("#")
    if (hex.length != 6) return DEFAULT_COLOR
    val rgb = hex.toLongOrNull(16) ?: return DEFAULT_COLOR
    return 0xFF000000L or rgb
}

private fun Long.toHexColor(): String {
    val rgb = this and 0xFFFFFFL
    return "#" + rgb.toString(16).padStart(6, '0')
}

private const val DEFAULT_COLOR = 0xFF4F46E5L
