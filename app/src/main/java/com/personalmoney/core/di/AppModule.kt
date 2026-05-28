package com.personalmoney.core.di

import android.content.Context
import com.personalmoney.core.data.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier annotation class IoDispatcher
@Qualifier annotation class DefaultDispatcher
@Qualifier annotation class MainDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences =
        UserPreferences(context)

    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    @Provides @MainDispatcher fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}
