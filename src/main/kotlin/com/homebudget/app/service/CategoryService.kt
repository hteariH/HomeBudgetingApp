package com.homebudget.app.service

import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.User
import com.homebudget.app.dto.CreateCategoryRequest
import com.homebudget.app.dto.UpdateCategoryRequest
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.SubscriptionRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class CategoryService(
    private val repo: CategoryRepository,
    private val transactionRepo: TransactionRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val securityUtils: SecurityUtils
) {

    fun findAll(type: CategoryType? = null, includeArchived: Boolean = true): List<Category> {
        val user = securityUtils.getCurrentUser()
        val all = if (type != null) repo.findByUserAndType(user, type) else repo.findAllByUser(user)
        return if (includeArchived) all else all.filter { !it.archived }
    }

    fun findById(id: Long): Category = repo.findByIdAndUser(id, securityUtils.getCurrentUser())
        ?: throw NoSuchElementException("Category not found: $id")

    /** Scale of the user's base currency - budget limits are denominated in it. */
    fun baseDecimals(): Int =
        securityUtils.getCurrentUser().baseCurrency?.decimals ?: DEFAULT_DECIMALS

    fun create(req: CreateCategoryRequest): Category {
        val user = securityUtils.getCurrentUser()
        val parent = req.parentId?.let { findById(it) }
        require(parent == null || parent.type == req.type) {
            "A ${req.type} category cannot sit under a ${parent?.type} parent"
        }
        // The database no longer enforces this, because sub-categories under different
        // parents may legitimately share a name. Siblings still may not.
        val duplicate = repo.findByUserAndType(user, req.type)
            .any { it.name.equals(req.name, ignoreCase = true) && it.parent?.id == req.parentId }
        require(!duplicate) { "Category '${req.name}' already exists here" }

        return repo.save(
            Category(
                name = req.name,
                user = user,
                type = req.type,
                icon = req.icon,
                color = req.color,
                description = req.description,
                parent = parent,
                sortOrder = req.sortOrder,
                budgetLimitMinor = req.budgetLimit?.let { MinorUnits.toMinor(it, baseDecimals()) }
            )
        )
    }

    fun update(id: Long, req: UpdateCategoryRequest): Category {
        val category = findById(id)
        req.name?.let { category.name = it }
        req.icon?.let { category.icon = it }
        req.color?.let { category.color = it }
        req.description?.let { category.description = it }
        req.archived?.let { category.archived = it }
        req.sortOrder?.let { category.sortOrder = it }
        req.parentId?.let { parentId ->
            require(parentId != id) { "A category cannot be its own parent" }
            val parent = findById(parentId)
            require(parent.type == category.type) {
                "A ${category.type} category cannot sit under a ${parent.type} parent"
            }
            category.parent = parent
        }
        // Null means "unchanged"; clearing needs an explicit flag.
        if (req.clearBudgetLimit) {
            category.budgetLimitMinor = null
        } else {
            req.budgetLimit?.let { category.budgetLimitMinor = MinorUnits.toMinor(it, baseDecimals()) }
        }
        req.mccCodes?.let {
            category.mccCodes.clear()
            category.mccCodes.addAll(it)
        }
        return repo.save(category)
    }

    /** Transactions plus subscriptions - both keep a category alive. */
    fun usageCount(id: Long): Long =
        transactionRepo.countByCategoryId(id) + subscriptionRepo.countByCategoryId(id)

    /**
     * Tombstones the category and unlinks its history instead of refusing.
     *
     * transactions.category_id is nullable precisely so that deleting a category does
     * not take the spending with it - the rows stay, uncategorised.
     */
    fun delete(id: Long) {
        val category = findById(id)
        val now = Instant.now()
        transactionRepo.detachCategory(category.id, now)
        category.deleted = true
        category.updatedAt = now
        repo.save(category)
    }

    fun seedDefaultCategories(user: User) {
        val defaults = listOf(
            Triple("Salary", CategoryType.INCOME, "briefcase") to "#10B981",
            Triple("Freelance", CategoryType.INCOME, "laptop") to "#3B82F6",
            Triple("Housing", CategoryType.EXPENSE, "home") to "#EF4444",
            Triple("Food", CategoryType.EXPENSE, "shopping-cart") to "#F59E0B",
            Triple("Transportation", CategoryType.EXPENSE, "truck") to "#6366F1",
            Triple("Entertainment", CategoryType.EXPENSE, "film") to "#EC4899",
            Triple("Utilities", CategoryType.EXPENSE, "zap") to "#8B5CF6",
            Triple("Other", CategoryType.EXPENSE, "tag") to "#6B7280"
        )
        repo.saveAll(
            defaults.mapIndexed { index, (spec, color) ->
                val (name, type, icon) = spec
                Category(
                    name = name,
                    type = type,
                    icon = icon,
                    color = color,
                    user = user,
                    isDefault = true,
                    sortOrder = index
                )
            }
        )
    }
}
