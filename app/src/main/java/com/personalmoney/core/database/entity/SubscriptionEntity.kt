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
    val note: String? = null
)
