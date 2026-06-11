package com.personalmoney.core.di

import android.content.Context
import androidx.room.Room
import com.personalmoney.core.database.ALL_MIGRATIONS
import com.personalmoney.core.database.AppDatabase
import com.personalmoney.core.database.dao.AccountDao
import com.personalmoney.core.database.dao.CategoryDao
import com.personalmoney.core.database.dao.CurrencyDao
import com.personalmoney.core.database.dao.ExchangeRateDao
import com.personalmoney.core.database.dao.SubscriptionDao
import com.personalmoney.core.database.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()

    @Provides fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideSubscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()
    @Provides fun provideCurrencyDao(db: AppDatabase): CurrencyDao = db.currencyDao()
    @Provides fun provideExchangeRateDao(db: AppDatabase): ExchangeRateDao = db.exchangeRateDao()
}
