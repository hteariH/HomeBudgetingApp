package com.personalmoney.core.di

import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.auth.BrowserTokenStore
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.data.RestCurrencyRepository
import com.personalmoney.core.data.RestAccountRepository
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
import org.koin.core.module.dsl.singleOf
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
    single { createJson() }
    single<HttpClientEngine> { Js.create() }
    singleOf(::BrowserTokenStore) bind TokenStore::class
    singleOf(::RestAccountRepository) bind AccountRepository::class
    singleOf(::RestCategoryRepository) bind CategoryRepository::class
    singleOf(::RestTransactionRepository) bind TransactionRepository::class
    singleOf(::RestCurrencyRepository) bind CurrencyRepository::class

    single<HttpClient> { createBackendClient(get(), get(), pageOrigin(), get()) }
    single { RestApi(get()) }
    single { HomeBudgetApi(get()) }
    singleOf(::AuthRepository)
}
