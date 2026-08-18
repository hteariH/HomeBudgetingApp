package com.personalmoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalmoney.core.domain.TransactionType
import kotlinx.datetime.Instant

@Entity(
    tableName = "transactions",
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
        ),
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscription_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("account_id"),
        Index("category_id"),
        Index("subscription_id"),
        Index("transfer_group_id"),
        Index("occurred_at"),
        Index(value = ["uuid"], unique = true),
        Index("pending_sync")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "account_amount_minor") val accountAmountMinor: Long,
    @ColumnInfo(name = "fx_rate_to_account") val fxRateToAccount: Double = 1.0,
    @ColumnInfo(name = "occurred_at") val occurredAt: Instant,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    val note: String? = null,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Long? = null,
    @ColumnInfo(name = "transfer_group_id") val transferGroupId: String? = null,
    @ColumnInfo(name = "transfer_counterpart_account_id") val transferCounterpartAccountId: Long? = null,
    @ColumnInfo(name = "transfer_fee_minor") val transferFeeMinor: Long? = null,
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
