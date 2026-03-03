package com.homebudget.app.service

import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.dto.CreateCategoryRequest
import com.homebudget.app.dto.UpdateCategoryRequest
import com.homebudget.app.repository.CategoryRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService(private val repo: CategoryRepository) {

    fun findAll(type: CategoryType? = null): List<Category> =
        if (type != null) repo.findByType(type) else repo.findAll()

    fun findById(id: Long): Category = repo.findByIdOrNull(id)
        ?: throw NoSuchElementException("Category not found: $id")

    fun create(req: CreateCategoryRequest): Category {
        if (repo.existsByNameAndType(req.name, req.type)) {
            throw IllegalArgumentException("Category '${req.name}' already exists for type ${req.type}")
        }
        return repo.save(
            Category(
                name = req.name,
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
}
