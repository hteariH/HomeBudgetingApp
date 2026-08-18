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
 * Asks for a sync after a local write.
 *
 * A separate, tiny type rather than a WorkManager call inside every repository: the
 * repositories are unit-tested with a fake, and pulling WorkManager into them would
 * drag the Android framework into those tests.
 *
 * The request is coalesced with `ExistingWorkPolicy.KEEP`, so entering five expenses in
 * a row queues one sync rather than five, and it waits for a network rather than
 * failing without one - the whole point being that the app works in a shop with no
 * signal.
 */
interface SyncTrigger {
    fun requestSync()
}

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
