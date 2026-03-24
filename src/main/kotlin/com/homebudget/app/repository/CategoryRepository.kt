package com.homebudget.app.repository

import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findAllByUser(user: User): List<Category>
    fun findByUserAndType(user: User, type: CategoryType): List<Category>
    fun existsByNameAndTypeAndUser(name: String, type: CategoryType, user: User): Boolean
    fun findByIdAndUser(id: Long, user: User): Category?

    @Query("SELECT DISTINCT c FROM Category c JOIN c.mccCodes m WHERE c.user = :user AND m = :mcc")
    fun findByUserAndMccCode(user: User, mcc: Int): List<Category>
}

