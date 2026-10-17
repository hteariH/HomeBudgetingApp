package com.personalmoney.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.personalmoney.core.database.dao.AccountDao
import com.personalmoney.core.database.dao.CategoryDao
import com.personalmoney.core.database.dao.CurrencyDao
import com.personalmoney.core.database.dao.ExchangeRateDao
import com.personalmoney.core.database.dao.SubscriptionDao
import com.personalmoney.core.database.dao.TransactionDao
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.database.entity.CurrencyEntity
import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.database.entity.SubscriptionEntity
import com.personalmoney.core.database.entity.TransactionEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        SubscriptionEntity::class,
        CurrencyEntity::class,
        ExchangeRateEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun exchangeRateDao(): ExchangeRateDao

    companion object {
        const val NAME = "personalmoney.db"
    }
}
