package com.personalmoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalmoney.core.domain.CategoryType

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("type"), Index("archived"), Index("parent_id")]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: Long,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    val archived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    // Optional monthly spending limit in BASE-currency minor units (expense categories only).
    @ColumnInfo(name = "budget_limit_minor") val budgetLimitMinor: Long? = null
)
