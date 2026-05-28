package com.personalmoney.core.database

import androidx.room.TypeConverter
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.ExchangeRateSource
import com.personalmoney.core.domain.SubscriptionFrequency
import com.personalmoney.core.domain.TransactionType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class Converters {
    @TypeConverter fun instantToEpochMs(value: Instant?): Long? = value?.toEpochMilliseconds()
    @TypeConverter fun epochMsToInstant(value: Long?): Instant? = value?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter fun localDateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter fun transactionTypeToString(value: TransactionType?): String? = value?.name
    @TypeConverter fun stringToTransactionType(value: String?): TransactionType? =
        value?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }

    @TypeConverter fun categoryTypeToString(value: CategoryType?): String? = value?.name
    @TypeConverter fun stringToCategoryType(value: String?): CategoryType? =
        value?.let { runCatching { CategoryType.valueOf(it) }.getOrNull() }

    @TypeConverter fun subFreqToString(value: SubscriptionFrequency?): String? = value?.name
    @TypeConverter fun stringToSubFreq(value: String?): SubscriptionFrequency? =
        value?.let { runCatching { SubscriptionFrequency.valueOf(it) }.getOrNull() }

    @TypeConverter fun fxSourceToString(value: ExchangeRateSource?): String? = value?.name
    @TypeConverter fun stringToFxSource(value: String?): ExchangeRateSource? =
        value?.let { runCatching { ExchangeRateSource.valueOf(it) }.getOrNull() }
}
