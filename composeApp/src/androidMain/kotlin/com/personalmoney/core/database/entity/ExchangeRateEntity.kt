package com.personalmoney.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.personalmoney.core.domain.ExchangeRateSource
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "exchange_rates",
    primaryKeys = ["base_code", "quote_code"]
)
data class ExchangeRateEntity(
    @ColumnInfo(name = "base_code") val baseCode: String,
    @ColumnInfo(name = "quote_code") val quoteCode: String,
    val rate: Double,
    val date: LocalDate,
    val source: ExchangeRateSource,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long
)
