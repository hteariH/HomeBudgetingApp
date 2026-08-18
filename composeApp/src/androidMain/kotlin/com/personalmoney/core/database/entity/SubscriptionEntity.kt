package com.personalmoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalmoney.core.domain.SubscriptionFrequency
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("account_id"), Index("category_id"), Index("active"), Index("next_charge_date")]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val frequency: SubscriptionFrequency,
    val interval: Int = 1,
    @ColumnInfo(name = "next_charge_date") val nextChargeDate: LocalDate,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate? = null,
    val active: Boolean = true,
    @ColumnInfo(name = "reminder_minutes_before") val reminderMinutesBefore: Int? = 24 * 60,
    val icon: String = "subscriptions",
    val color: Long = 0xFF008080L,
    val note: String? = null,
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
