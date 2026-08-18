package com.personalmoney.core.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.personalmoney.BuildConfig
import com.personalmoney.core.auth.AuthInterceptor
import com.personalmoney.core.auth.TokenAuthenticator
import com.personalmoney.core.currency.FrankfurterApi
import com.personalmoney.core.network.HomeBudgetApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The plain client, used for third-party APIs that must not receive our bearer token. */
@Qualifier annotation class PlainClient

/** The authenticated client for our own backend. */
@Qualifier annotation class BackendClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // v2 host (api.frankfurter.dev) — ~165 currencies incl. UAH; the legacy api.frankfurter.app
    // only exposes the ~30 ECB currencies and silently omits UAH.
    // Kept as a fallback for a signed-out user; once signed in, rates come from our
    // own server so every client converts with identical numbers.
    private const val FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/"

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun baseClient(): OkHttpClient.Builder {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC even in debug: BODY would put bearer tokens and balances in logcat.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
    }

    @Provides @Singleton @PlainClient
    fun providePlainClient(): OkHttpClient = baseClient().build()

    @Provides @Singleton @BackendClient
    fun provideBackendClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient = baseClient()
        .addInterceptor(authInterceptor)
        // OkHttp retries the request itself once this hands back a refreshed token,
        // so no call site has to know about token expiry.
        .authenticator(tokenAuthenticator)
        .build()

    @Provides @Singleton
    fun provideHomeBudgetApi(@BackendClient okHttp: OkHttpClient, json: Json): HomeBudgetApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(HomeBudgetApi::class.java)
    }

    @Provides @Singleton
    fun provideFrankfurterApi(@PlainClient okHttp: OkHttpClient, json: Json): FrankfurterApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(FRANKFURTER_BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(FrankfurterApi::class.java)
    }
}
