package com.personalmoney.feature.subscriptions

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SubscriptionWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
