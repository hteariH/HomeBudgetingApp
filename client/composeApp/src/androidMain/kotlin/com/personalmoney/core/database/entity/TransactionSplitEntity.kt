package com.personalmoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_splits",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("transaction_id"),
        Index("category_id"),
        Index(value = ["uuid"], unique = true),
        Index("pending_sync")
    ]
)
data class TransactionSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val note: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "uuid", defaultValue = "''") override val uuid: String = newUuid(),
    @ColumnInfo(name = "updated_at", defaultValue = "0") override val updatedAt: Long = 0,
    @ColumnInfo(name = "deleted", defaultValue = "0") override val deleted: Boolean = false,
    @ColumnInfo(name = "pending_sync", defaultValue = "1") override val pendingSync: Boolean = true
) : SyncableEntity
