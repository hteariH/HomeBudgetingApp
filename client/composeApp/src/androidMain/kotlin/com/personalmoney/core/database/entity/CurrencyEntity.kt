package com.personalmoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "currencies",
    indices = [Index(value = ["uuid"], unique = true), Index("pending_sync")]
)
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int = 2,
    val active: Boolean = false,
    val sortOrder: Int = 0,
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
