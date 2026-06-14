package com.personalmoney.core.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.personalmoney.core.currency.FrankfurterApi
import com.personalmoney.core.homemoney.HomeMoneyApi
import com.personalmoney.core.homemoney.HomeMoneyConfig
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // v2 host (api.frankfurter.dev) — ~165 currencies incl. UAH; the legacy api.frankfurter.app
    // only exposes the ~30 ECB currencies and silently omits UAH.
    private const val FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/"

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides @Singleton
    fun provideFrankfurterApi(okHttp: OkHttpClient, json: Json): FrankfurterApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(FRANKFURTER_BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(FrankfurterApi::class.java)
    }

    @Provides @Singleton
    fun provideHomeMoneyApi(okHttp: OkHttpClient, json: Json): HomeMoneyApi {
        // ASMX returns "application/json; charset=utf-8"; the converter matches on application/json.
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(HomeMoneyConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(HomeMoneyApi::class.java)
    }
}
