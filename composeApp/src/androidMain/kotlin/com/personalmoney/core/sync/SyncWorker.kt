package com.personalmoney.core.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Runs a sync pass in the background.
 *
 * Built on the same `@HiltWorker` pattern as the existing FX and subscription workers.
 * A failed pass returns `retry()` rather than `failure()`: the usual reason is no
 * network or a server restart, and the pending rows are still safely on the device.
 */
class SyncWorker constructor(
    context: Context,
    params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val result = syncEngine.syncNow()
        Log.d(TAG, "Synced: pulled=${result.pulled} pushed=${result.pushed} rejected=${result.rejected}")
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Sync failed, will retry: ${e.message}")
        Result.retry()
    }

    companion object {
        const val TAG = "SyncWorker"
        const val UNIQUE_NAME = "sync-now"
        const val PERIODIC_NAME = "sync-periodic"
    }
}

/**
 * Schedules the recurring pass.
 *
 * The periodic job is the safety net for changes made elsewhere - a transaction added
 * on the web has nothing on this device to trigger a sync. Immediate pushes after a
 * local write come from [SyncTrigger] instead.
 */
class SyncScheduler constructor(
    private val context: Context
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
