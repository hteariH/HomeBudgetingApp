package com.personalmoney.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int = 2,
    val active: Boolean = false,
    val sortOrder: Int = 0
)
