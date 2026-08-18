package com.personalmoney.core.di

import com.personalmoney.core.sync.SyncTrigger
import com.personalmoney.core.sync.WorkManagerSyncTrigger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [SyncTrigger] is an interface so repositories can be unit-tested without dragging
 * WorkManager - and therefore the Android framework - into those tests.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindSyncTrigger(impl: WorkManagerSyncTrigger): SyncTrigger
}
