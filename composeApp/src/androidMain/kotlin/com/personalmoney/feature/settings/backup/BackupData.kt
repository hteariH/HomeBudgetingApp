package com.personalmoney.feature.settings.backup

import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.database.entity.CurrencyEntity
import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.database.entity.SubscriptionEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.ExchangeRateSource
import com.personalmoney.core.domain.SubscriptionFrequency
import com.personalmoney.core.domain.TransactionType
import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvelope(
    val version: Int = 1,
    val exportedAt: Long,
    val accounts: List<AccountDto>,
    val categories: List<CategoryDto>,
    val transactions: List<TransactionDto>,
    val subscriptions: List<SubscriptionDto>,
    val currencies: List<CurrencyDto>,
    val exchangeRates: List<ExchangeRateDto>
)

@Serializable
data class AccountDto(
    val id: Long, val name: String, val currencyCode: String,
    val initialBalanceMinor: Long, val icon: String, val color: Long,
    val archived: Boolean, val sortOrder: Int, val includeInTotal: Boolean,
    val note: String? = null
) {
    fun toEntity() = AccountEntity(id, name, currencyCode, initialBalanceMinor, icon, color, archived, sortOrder, includeInTotal, note)
    companion object {
        fun from(e: AccountEntity) = AccountDto(e.id, e.name, e.currencyCode, e.initialBalanceMinor, e.icon, e.color, e.archived, e.sortOrder, e.includeInTotal, e.note)
    }
}

@Serializable
data class CategoryDto(
    val id: Long, val name: String, val type: String,
    val icon: String, val color: Long, val parentId: Long? = null,
    val archived: Boolean, val sortOrder: Int, val isDefault: Boolean,
    val budgetLimitMinor: Long? = null
) {
    fun toEntity() = CategoryEntity(id, name, CategoryType.valueOf(type), icon, color, parentId, archived, sortOrder, isDefault, budgetLimitMinor)
    companion object {
        fun from(e: CategoryEntity) = CategoryDto(e.id, e.name, e.type.name, e.icon, e.color, e.parentId, e.archived, e.sortOrder, e.isDefault, e.budgetLimitMinor)
    }
}

@Serializable
data class TransactionDto(
    val id: Long, val type: String, val accountId: Long, val categoryId: Long?,
    val amountMinor: Long, val currencyCode: String, val accountAmountMinor: Long,
    val fxRateToAccount: Double, val occurredAtEpochMs: Long, val createdAtEpochMs: Long,
    val note: String? = null, val subscriptionId: Long? = null,
    val transferGroupId: String? = null, val transferCounterpartAccountId: Long? = null,
    val transferFeeMinor: Long? = null
) {
    fun toEntity() = TransactionEntity(
        id = id,
        type = TransactionType.valueOf(type),
        accountId = accountId,
        categoryId = categoryId,
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        accountAmountMinor = accountAmountMinor,
        fxRateToAccount = fxRateToAccount,
        occurredAt = kotlin.time.Instant.fromEpochMilliseconds(occurredAtEpochMs),
        createdAt = kotlin.time.Instant.fromEpochMilliseconds(createdAtEpochMs),
        note = note,
        subscriptionId = subscriptionId,
        transferGroupId = transferGroupId,
        transferCounterpartAccountId = transferCounterpartAccountId,
        transferFeeMinor = transferFeeMinor
    )
    companion object {
        fun from(e: TransactionEntity) = TransactionDto(
            id = e.id,
            type = e.type.name,
            accountId = e.accountId,
            categoryId = e.categoryId,
            amountMinor = e.amountMinor,
            currencyCode = e.currencyCode,
            accountAmountMinor = e.accountAmountMinor,
            fxRateToAccount = e.fxRateToAccount,
            occurredAtEpochMs = e.occurredAt.toEpochMilliseconds(),
            createdAtEpochMs = e.createdAt.toEpochMilliseconds(),
            note = e.note,
            subscriptionId = e.subscriptionId,
            transferGroupId = e.transferGroupId,
            transferCounterpartAccountId = e.transferCounterpartAccountId,
            transferFeeMinor = e.transferFeeMinor
        )
    }
}

@Serializable
data class SubscriptionDto(
    val id: Long, val name: String, val accountId: Long, val categoryId: Long?,
    val amountMinor: Long, val currencyCode: String, val frequency: String, val interval: Int,
    val nextChargeDate: String, val startDate: String, val endDate: String? = null,
    val active: Boolean, val reminderMinutesBefore: Int? = null,
    val icon: String, val color: Long, val note: String? = null
) {
    fun toEntity() = SubscriptionEntity(
        id, name, accountId, categoryId, amountMinor, currencyCode,
        SubscriptionFrequency.valueOf(frequency), interval,
        kotlinx.datetime.LocalDate.parse(nextChargeDate),
        kotlinx.datetime.LocalDate.parse(startDate),
        endDate?.let { kotlinx.datetime.LocalDate.parse(it) },
        active, reminderMinutesBefore, icon, color, note
    )
    companion object {
        fun from(e: SubscriptionEntity) = SubscriptionDto(
            e.id, e.name, e.accountId, e.categoryId, e.amountMinor, e.currencyCode,
            e.frequency.name, e.interval, e.nextChargeDate.toString(), e.startDate.toString(),
            e.endDate?.toString(), e.active, e.reminderMinutesBefore, e.icon, e.color, e.note
        )
    }
}

@Serializable
data class CurrencyDto(
    val code: String, val name: String, val symbol: String,
    val decimals: Int, val active: Boolean, val sortOrder: Int
) {
    fun toEntity() = CurrencyEntity(code, name, symbol, decimals, active, sortOrder)
    companion object {
        fun from(e: CurrencyEntity) = CurrencyDto(e.code, e.name, e.symbol, e.decimals, e.active, e.sortOrder)
    }
}

@Serializable
data class ExchangeRateDto(
    val baseCode: String, val quoteCode: String, val rate: Double,
    val date: String, val source: String, val updatedAtEpochMs: Long
) {
    fun toEntity() = ExchangeRateEntity(
        baseCode, quoteCode, rate,
        kotlinx.datetime.LocalDate.parse(date),
        ExchangeRateSource.valueOf(source),
        updatedAtEpochMs
    )
    companion object {
        fun from(e: ExchangeRateEntity) = ExchangeRateDto(
            e.baseCode, e.quoteCode, e.rate, e.date.toString(), e.source.name, e.updatedAtEpochMs
        )
    }
}
