package com.personalmoney.core.di

import androidx.room.Room
import androidx.room.withTransaction
import com.personalmoney.BuildConfig
import com.personalmoney.ads.AdsConsentManager
import com.personalmoney.ads.AdsInitializer
import com.personalmoney.ads.InterstitialAdManager
import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.currency.FrankfurterApi
import com.personalmoney.core.currency.FxRefreshWorker
import com.personalmoney.core.currency.FxScheduler
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.data.repository.SubscriptionRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.ALL_MIGRATIONS
import com.personalmoney.core.database.AppDatabase
import com.personalmoney.core.database.Transactor
import com.personalmoney.core.database.seed.DatabaseSeeder
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.createBackendClient
import com.personalmoney.core.network.createJson
import com.personalmoney.core.network.createPlainClient
import com.personalmoney.core.sync.SyncEngine
import com.personalmoney.core.sync.SyncScheduler
import com.personalmoney.core.sync.SyncTrigger
import com.personalmoney.core.sync.SyncWorker
import com.personalmoney.core.sync.WorkManagerSyncTrigger
import com.personalmoney.feature.accounts.AccountEditViewModel
import com.personalmoney.feature.accounts.AccountPickerViewModel
import com.personalmoney.feature.accounts.AccountsViewModel
import com.personalmoney.feature.analytics.AnalyticsViewModel
import com.personalmoney.feature.auth.AuthViewModel
import com.personalmoney.feature.categories.CategoriesViewModel
import com.personalmoney.feature.categories.CategoryEditViewModel
import com.personalmoney.feature.currencies.CurrenciesViewModel
import com.personalmoney.feature.currencies.CurrencyEditViewModel
import com.personalmoney.feature.dashboard.DashboardViewModel
import com.personalmoney.feature.onboarding.OnboardingViewModel
import com.personalmoney.feature.quickadd.QuickAddViewModel
import com.personalmoney.feature.settings.SettingsViewModel
import com.personalmoney.feature.settings.backup.BackupManager
import com.personalmoney.feature.subscriptions.SubscriptionEditViewModel
import com.personalmoney.feature.subscriptions.SubscriptionWorkScheduler
import com.personalmoney.feature.subscriptions.SubscriptionWorker
import com.personalmoney.feature.subscriptions.SubscriptionsViewModel
import com.personalmoney.feature.transactions.TransactionEditViewModel
import com.personalmoney.feature.transactions.TransactionsViewModel
import com.personalmoney.feature.transfers.TransferViewModel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin replaces Hilt because Hilt is JVM/Android-only and cannot follow this code to iOS
 * or wasm. Definitions are explicit rather than annotation-generated so the graph stays
 * readable and so nothing depends on a code generator that has to work on three platforms.
 *
 * `singleOf(::Type)` resolves each constructor parameter by type; `androidContext()` in
 * the Application makes `Context` resolvable, which is what the old `@ApplicationContext`
 * qualifier did.
 */

val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()
    }
    single<Transactor> {
        val db: AppDatabase = get()
        object : Transactor {
            override suspend fun <R> transaction(block: suspend () -> R): R =
                db.withTransaction { block() }
        }
    }
    single { get<AppDatabase>().accountDao() }
    single { get<AppDatabase>().categoryDao() }
    single { get<AppDatabase>().transactionDao() }
    single { get<AppDatabase>().subscriptionDao() }
    single { get<AppDatabase>().currencyDao() }
    single { get<AppDatabase>().exchangeRateDao() }
    singleOf(::DatabaseSeeder)
}

val networkModule = module {
    // v2 host (api.frankfurter.dev) — ~165 currencies incl. UAH; the legacy
    // api.frankfurter.app only exposes the ~30 ECB currencies and silently omits UAH.
    // Kept as a fallback for a signed-out user; once signed in, rates come from our own
    // server so every client converts with identical numbers.
    val frankfurterBaseUrl = "https://api.frankfurter.dev/"

    single { createJson() }
    // OkHttp is the Ktor engine on Android. On iOS this becomes Darwin and on wasm Js;
    // only this line changes, because everything above it is engine-agnostic.
    single<HttpClientEngine> { OkHttp.create() }
    single { HomeBudgetApi(createBackendClient(get(), get(), BuildConfig.API_BASE_URL, get())) }
    single { FrankfurterApi(createPlainClient(get(), get(), frankfurterBaseUrl)) }
}

val dataModule = module {
    singleOf(::UserPreferences)
    singleOf(::TokenStore)
    singleOf(::AuthRepository)
    singleOf(::AccountRepository)
    singleOf(::CategoryRepository)
    singleOf(::CurrencyRepository)
    singleOf(::ExchangeRateRepository)
    singleOf(::SubscriptionRepository)
    singleOf(::TransactionRepository)
    singleOf(::BackupManager)
    singleOf(::SyncEngine)
    // SyncTrigger is an interface so repositories can be unit-tested without dragging
    // WorkManager - and therefore the Android framework - into those tests.
    singleOf(::WorkManagerSyncTrigger) bind SyncTrigger::class
    singleOf(::SyncScheduler)
    singleOf(::FxScheduler)
    singleOf(::SubscriptionWorkScheduler)
}

val adsModule = module {
    singleOf(::AdsConsentManager)
    singleOf(::AdsInitializer)
    singleOf(::InterstitialAdManager)
}

val workerModule = module {
    workerOf(::SyncWorker)
    workerOf(::FxRefreshWorker)
    workerOf(::SubscriptionWorker)
}

val viewModelModule = module {
    viewModelOf(::AccountEditViewModel)
    viewModelOf(::AccountPickerViewModel)
    viewModelOf(::AccountsViewModel)
    viewModelOf(::AnalyticsViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::CategoriesViewModel)
    viewModelOf(::CategoryEditViewModel)
    viewModelOf(::CurrenciesViewModel)
    viewModelOf(::CurrencyEditViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::QuickAddViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SubscriptionEditViewModel)
    viewModelOf(::SubscriptionsViewModel)
    viewModelOf(::TransactionEditViewModel)
    viewModelOf(::TransactionsViewModel)
    viewModelOf(::TransferViewModel)
}

/**
 * The whole graph as one module.
 *
 * `includes` rather than a list so the parts stay separable but resolve as a single unit:
 * AuthRepository lives in [dataModule] and depends on HomeBudgetApi from [networkModule],
 * and a per-module check would call that a missing definition.
 */
val appModule = module {
    includes(
        databaseModule,
        networkModule,
        dataModule,
        adsModule,
        workerModule,
        viewModelModule,
    )
}
