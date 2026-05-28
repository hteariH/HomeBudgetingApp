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
        Index("occurred_at")
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
    @ColumnInfo(name = "transfer_fee_minor") val transferFeeMinor: Long? = null
)
