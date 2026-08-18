package com.personalmoney.core.database.seed

import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.domain.CategoryType

object DefaultCategories {
    val list: List<CategoryEntity> = listOf(
        // Expense
        CategoryEntity(name = "Food & Groceries",   type = CategoryType.EXPENSE, icon = "restaurant",      color = 0xFFE5484DL, sortOrder = 0,  isDefault = true),
        CategoryEntity(name = "Transport",          type = CategoryType.EXPENSE, icon = "directions_car",  color = 0xFFFF9F1CL, sortOrder = 1,  isDefault = true),
        CategoryEntity(name = "Housing",            type = CategoryType.EXPENSE, icon = "home",            color = 0xFF3E63DDL, sortOrder = 2,  isDefault = true),
        CategoryEntity(name = "Utilities",          type = CategoryType.EXPENSE, icon = "bolt",            color = 0xFFFFCC00L, sortOrder = 3,  isDefault = true),
        CategoryEntity(name = "Entertainment",      type = CategoryType.EXPENSE, icon = "movie",           color = 0xFFAB47BCL, sortOrder = 4,  isDefault = true),
        CategoryEntity(name = "Health",             type = CategoryType.EXPENSE, icon = "favorite",        color = 0xFF30A46CL, sortOrder = 5,  isDefault = true),
        CategoryEntity(name = "Shopping",           type = CategoryType.EXPENSE, icon = "shopping_bag",    color = 0xFFEC4899L, sortOrder = 6,  isDefault = true),
        CategoryEntity(name = "Cafés & Restaurants",type = CategoryType.EXPENSE, icon = "local_cafe",      color = 0xFF8B4513L, sortOrder = 7,  isDefault = true),
        CategoryEntity(name = "Education",          type = CategoryType.EXPENSE, icon = "school",          color = 0xFF06B6D4L, sortOrder = 8,  isDefault = true),
        CategoryEntity(name = "Travel",             type = CategoryType.EXPENSE, icon = "flight",          color = 0xFF6366F1L, sortOrder = 9,  isDefault = true),
        CategoryEntity(name = "Subscriptions",      type = CategoryType.EXPENSE, icon = "subscriptions",   color = 0xFF008080L, sortOrder = 10, isDefault = true),
        CategoryEntity(name = "Gifts",              type = CategoryType.EXPENSE, icon = "redeem",          color = 0xFFEF4444L, sortOrder = 11, isDefault = true),
        CategoryEntity(name = "Pets",               type = CategoryType.EXPENSE, icon = "pets",            color = 0xFF92400EL, sortOrder = 12, isDefault = true),
        CategoryEntity(name = "Sport & Fitness",    type = CategoryType.EXPENSE, icon = "fitness_center",  color = 0xFF0EA5E9L, sortOrder = 13, isDefault = true),
        CategoryEntity(name = "Personal Care",      type = CategoryType.EXPENSE, icon = "spa",             color = 0xFFD946EFL, sortOrder = 14, isDefault = true),
        CategoryEntity(name = "Taxes & Fees",       type = CategoryType.EXPENSE, icon = "account_balance", color = 0xFF6B7280L, sortOrder = 15, isDefault = true),
        CategoryEntity(name = "Other",              type = CategoryType.EXPENSE, icon = "more_horiz",      color = 0xFF94A3B8L, sortOrder = 99, isDefault = true),

        // Income
        CategoryEntity(name = "Salary",             type = CategoryType.INCOME, icon = "payments",          color = 0xFF30A46CL, sortOrder = 0,  isDefault = true),
        CategoryEntity(name = "Freelance",          type = CategoryType.INCOME, icon = "work",              color = 0xFF06B6D4L, sortOrder = 1,  isDefault = true),
        CategoryEntity(name = "Bonus",              type = CategoryType.INCOME, icon = "stars",             color = 0xFFFFCC00L, sortOrder = 2,  isDefault = true),
        CategoryEntity(name = "Investments",        type = CategoryType.INCOME, icon = "trending_up",       color = 0xFF6366F1L, sortOrder = 3,  isDefault = true),
        CategoryEntity(name = "Gifts",              type = CategoryType.INCOME, icon = "redeem",            color = 0xFFD946EFL, sortOrder = 4,  isDefault = true),
        CategoryEntity(name = "Refund",             type = CategoryType.INCOME, icon = "undo",              color = 0xFF3E63DDL, sortOrder = 5,  isDefault = true),
        CategoryEntity(name = "Other",              type = CategoryType.INCOME, icon = "more_horiz",        color = 0xFF94A3B8L, sortOrder = 99, isDefault = true)
    )
}
