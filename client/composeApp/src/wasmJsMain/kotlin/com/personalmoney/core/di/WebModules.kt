package com.personalmoney.core.di

import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.auth.BrowserTokenStore
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.BankRepository
import com.personalmoney.core.data.CsvImportRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.BrowserKeyValueStore
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.data.KeyValueStore
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.sync.NoOpSyncTrigger
import com.personalmoney.core.sync.SyncTrigger
import com.personalmoney.core.data.ExchangeRateRepository
import com.personalmoney.core.data.RestExchangeRateRepository
import com.personalmoney.core.data.RestSubscriptionRepository
import com.personalmoney.core.data.SubscriptionRepository
import com.personalmoney.core.data.RestCurrencyRepository
import com.personalmoney.core.data.RestAccountRepository
import com.personalmoney.core.data.RestBankRepository
import com.personalmoney.core.data.RestCsvImportRepository
import com.personalmoney.core.data.RestCategoryRepository
import com.personalmoney.core.data.RestTransactionRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.createBackendClient
import com.personalmoney.core.network.createJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import com.personalmoney.feature.banks.BanksViewModel
import com.personalmoney.feature.banks.CsvImportViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

@JsFun("() => window.location.origin")
private external fun pageOrigin(): String

/**
 * The web object graph.
 *
 * Deliberately smaller than the Android one: no Room, no WorkManager, no sync engine. A
 * browser tab cannot usefully queue offline writes, so the web client reads and writes
 * [RestApi] directly and keeps no local database.
 *
 * The base URL is the page's own origin because the bundle is served by the same Spring
 * app that owns /api, which is also why no CORS configuration is needed.
 */
val webModule = module {
    includes(sharedViewModelModule)
    single { createJson() }
    single<HttpClientEngine> { Js.create() }
    singleOf(::BrowserTokenStore) bind TokenStore::class
    singleOf(::BrowserKeyValueStore) bind KeyValueStore::class
    singleOf(::UserPreferences)
    single<SyncTrigger> { NoOpSyncTrigger }
    singleOf(::RestAccountRepository) bind AccountRepository::class
    singleOf(::RestCategoryRepository) bind CategoryRepository::class
    singleOf(::RestTransactionRepository) bind TransactionRepository::class
    singleOf(::RestCurrencyRepository) bind CurrencyRepository::class
    singleOf(::RestSubscriptionRepository) bind SubscriptionRepository::class
    singleOf(::RestExchangeRateRepository) bind ExchangeRateRepository::class

    // Web only. The phone has no bank screen, and Android's Koin verify test would
    // fail loudly if BanksViewModel were ever registered in the shared module without
    // this binding underneath it - which is the check working, not a nuisance.
    singleOf(::RestBankRepository) bind BankRepository::class
    singleOf(::RestCsvImportRepository) bind CsvImportRepository::class
    viewModelOf(::BanksViewModel)
    viewModelOf(::CsvImportViewModel)

    single<HttpClient> { createBackendClient(get(), get(), pageOrigin(), get()) }
    single { RestApi(get()) }
    single { HomeBudgetApi(get()) }
    singleOf(::AuthRepository)
}
