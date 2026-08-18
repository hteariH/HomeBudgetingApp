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
    indices = [Index("type"), Index("archived"), Index("parent_id"), Index(value = ["uuid"], unique = true), Index("pending_sync")]
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
    @ColumnInfo(name = "budget_limit_minor") val budgetLimitMinor: Long? = null,
    // ── Sync metadata ───────────────────────────────────────────────────────
    // uuid is the identity the server keys on; it is minted here so a row created
    // offline can be referenced before the server has ever seen it.
    // updatedAt is the *server's* stamp for the version this row is based on (0 =
    // never synced), and is what the push sends as baseUpdatedAt.
    // deleted is a tombstone - a hard delete would be invisible to the server and
    // the row would come straight back on the next pull.
    // pendingSync marks a local change that has not been accepted yet.
    @ColumnInfo(name = "uuid", defaultValue = "''") override val uuid: String = newUuid(),
    @ColumnInfo(name = "updated_at", defaultValue = "0") override val updatedAt: Long = 0,
    @ColumnInfo(name = "deleted", defaultValue = "0") override val deleted: Boolean = false,
    @ColumnInfo(name = "pending_sync", defaultValue = "1") override val pendingSync: Boolean = true
) : SyncableEntity
