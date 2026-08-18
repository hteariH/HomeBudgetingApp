package com.personalmoney.core.di

import com.personalmoney.BuildConfig
import com.personalmoney.core.auth.TokenStore
import com.personalmoney.core.currency.FrankfurterApi
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.createBackendClient
import com.personalmoney.core.network.createJson
import com.personalmoney.core.network.createPlainClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // v2 host (api.frankfurter.dev) — ~165 currencies incl. UAH; the legacy api.frankfurter.app
    // only exposes the ~30 ECB currencies and silently omits UAH.
    // Kept as a fallback for a signed-out user; once signed in, rates come from our
    // own server so every client converts with identical numbers.
    private const val FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/"

    @Provides @Singleton
    fun provideJson(): Json = createJson()

    /**
     * OkHttp is the Ktor engine on Android. On iOS this becomes Darwin and on wasm Js;
     * only this provider changes, because everything above it is engine-agnostic.
     */
    @Provides @Singleton
    fun provideEngine(): HttpClientEngine = OkHttp.create()

    @Provides @Singleton
    fun provideHomeBudgetApi(
        engine: HttpClientEngine,
        json: Json,
        tokenStore: TokenStore,
    ): HomeBudgetApi = HomeBudgetApi(
        createBackendClient(engine, json, BuildConfig.API_BASE_URL, tokenStore)
    )

    @Provides @Singleton
    fun provideFrankfurterApi(engine: HttpClientEngine, json: Json): FrankfurterApi =
        FrankfurterApi(createPlainClient(engine, json, FRANKFURTER_BASE_URL))
}
