package com.homebudget.app.repository

import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findByType(type: CategoryType): List<Category>
    fun existsByNameAndType(name: String, type: CategoryType): Boolean
}
