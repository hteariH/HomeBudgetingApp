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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The shared category contract, backed by REST. See [RestAccountRepository]. */
class RestCategoryRepository(private val api: RestApi) : CategoryRepository {

    private val cache = MutableStateFlow<List<Category>>(emptyList())

    /**
     * See [LazyRefresh]. The screens are written against Room's guarantee - collect and
     * rows arrive - and a REST cache is empty until something asks the server.
     */
    private val lazyLoad = LazyRefresh { refresh() }

    /** The cache, but collecting it fetches first if nothing has yet. */
    private val loaded get() = cache.loadingOnce(lazyLoad)

    /** categoryId -> how many recent transactions use it; filled by [refresh]. */
    private val recentUses = MutableStateFlow<Map<Long, Int>>(emptyMap())

    override fun observeByType(type: CategoryType): Flow<List<Category>> =
        loaded.map { all -> all.filter { it.type == type && !it.archived }.sortedBy { it.sortOrder } }

    override fun observeAll(): Flow<List<Category>> = loaded

    /**
     * Ordered by how often each category appears in the recent ledger, then by name.
     *
     * Android gets this from a GROUP BY over the whole history; there is no such endpoint,
     * so this counts the page of transactions the app has already pulled. Different data,
     * same intent - the chips put what the user actually uses within reach.
     */
    override fun observeMostUsed(type: CategoryType, limit: Int): Flow<List<Category>> =
        combine(loaded, recentUses) { all, uses ->
            all.asSequence()
                .filter { it.type == type && !it.archived }
                .sortedWith(compareByDescending<Category> { uses[it.id] ?: 0 }.thenBy { it.name })
                .take(limit)
                .toList()
        }

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

    /**
     * Asks the ledger endpoint for one row and reads the total off the page, rather than
     * pulling every transaction in the category to count them.
     */
    override suspend fun usageCount(id: Long): Int =
        api.transactions(categoryId = id, size = 1).totalElements.toInt()

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
        recentUses.value = api.transactions(size = USAGE_SAMPLE).content
            .mapNotNull { it.category?.id }
            .groupingBy { it }
            .eachCount()
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

/** How far back the "most used" ordering looks. The same page the dashboard pulls. */
private const val USAGE_SAMPLE = 100
