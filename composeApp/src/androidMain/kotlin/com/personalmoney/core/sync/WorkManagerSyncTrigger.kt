package com.personalmoney.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * The Android [SyncTrigger]: a coalesced WorkManager request.
 *
 * `ExistingWorkPolicy.KEEP` means entering five expenses in a row queues one sync rather
 * than five, and it waits for a network rather than failing without one - the whole point
 * being that the app works in a shop with no signal.
 */
class WorkManagerSyncTrigger constructor(
    private val context: Context
) : SyncTrigger {

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
