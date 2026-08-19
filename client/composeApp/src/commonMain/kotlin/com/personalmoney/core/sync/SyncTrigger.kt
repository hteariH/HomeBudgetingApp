package com.personalmoney.core.sync

/**
 * Asks for a sync after a local write.
 *
 * A separate, tiny type rather than a WorkManager call inside every repository: the
 * repositories are unit-tested with a fake, and pulling WorkManager into them would
 * drag the Android framework into those tests. Shared code holds the interface because
 * ViewModels ask for a sync after signing in; only Android has something to schedule.
 */
interface SyncTrigger {
    fun requestSync()
}

/** Does nothing. The web client has no local database, so there is nothing to converge. */
object NoOpSyncTrigger : SyncTrigger {
    override fun requestSync() = Unit
}
