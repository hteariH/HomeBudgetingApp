package com.homebudget.app.service

import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.User
import com.homebudget.app.dto.CreateCategoryRequest
import com.homebudget.app.dto.UpdateCategoryRequest
import com.homebudget.app.repository.CategoryRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService(
    private val repo: CategoryRepository,
    private val securityUtils: com.homebudget.app.security.SecurityUtils
) {

    fun findAll(type: CategoryType? = null): List<Category> {
        val user = securityUtils.getCurrentUser()
        return if (type != null) repo.findByUserAndType(user, type) else repo.findAllByUser(user)
    }

    fun findById(id: Long): Category = repo.findByIdAndUser(id, securityUtils.getCurrentUser())
        ?: throw NoSuchElementException("Category not found: $id")

    fun create(req: CreateCategoryRequest): Category {
        val user = securityUtils.getCurrentUser()
        if (repo.existsByNameAndTypeAndUser(req.name, req.type, user)) {
            throw IllegalArgumentException("Category '${req.name}' already exists for type ${req.type}")
        }
        return repo.save(
            Category(
                name = req.name,
                user = user,
                type = req.type,
                icon = req.icon,
                color = req.color,
                description = req.description
            )
        )
    }

    fun update(id: Long, req: UpdateCategoryRequest): Category {
        val category = findById(id)
        val updated = category.copy(
            name = req.name ?: category.name,
            icon = req.icon ?: category.icon,
            color = req.color ?: category.color,
            description = req.description ?: category.description
        )
        return repo.save(updated)
    }

    fun delete(id: Long) {
        val category = findById(id)
        repo.delete(category)
    }

    fun seedDefaultCategories(user: User) {
        val defaults = listOf(
            Category(name = "Salary", type = CategoryType.INCOME, icon = "briefcase", color = "#10B981", user = user),
            Category(name = "Freelance", type = CategoryType.INCOME, icon = "laptop", color = "#3B82F6", user = user),
            Category(name = "Housing", type = CategoryType.EXPENSE, icon = "home", color = "#EF4444", user = user),
            Category(name = "Food", type = CategoryType.EXPENSE, icon = "shopping-cart", color = "#F59E0B", user = user),
            Category(name = "Transportation", type = CategoryType.EXPENSE, icon = "truck", color = "#6366F1", user = user),
            Category(name = "Entertainment", type = CategoryType.EXPENSE, icon = "film", color = "#EC4899", user = user),
            Category(name = "Utilities", type = CategoryType.EXPENSE, icon = "zap", color = "#8B5CF6", user = user),
            Category(name = "Other", type = CategoryType.EXPENSE, icon = "tag", color = "#6B7280", user = user)
        )
        repo.saveAll(defaults)
    }
}
