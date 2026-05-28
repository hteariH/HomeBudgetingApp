package com.personalmoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index("archived"), Index("sort_order")]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "initial_balance_minor") val initialBalanceMinor: Long = 0,
    val icon: String = "wallet",
    val color: Long = 0xFF3D4499L,
    val archived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "include_in_total") val includeInTotal: Boolean = true,
    val note: String? = null
)
