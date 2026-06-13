package com.personalmoney.core.currency

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FxScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<FxRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(networkConstraints)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FxRefreshWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Fetches rates promptly (runs as soon as a network is available) rather than waiting for the
     * daily periodic job. Called on launch so newly-added currencies get a rate without a long wait.
     * KEEP avoids piling up duplicate runs if one is already pending.
     */
    fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<FxRefreshWorker>()
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FxRefreshWorker.ONE_TIME_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
