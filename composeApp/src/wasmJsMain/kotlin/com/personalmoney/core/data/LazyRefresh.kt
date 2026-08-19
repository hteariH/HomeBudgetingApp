package com.personalmoney.core.data

import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fetches once, the first time anything actually looks.
 *
 * The screens are written against Room's guarantee: collect a repository flow and rows
 * arrive. Room delivers that because the database is already there; a REST repository has
 * an empty cache until something asks the server. Without this, every web screen renders
 * correctly and shows nothing - which is what happened the first time the real navigation
 * host ran in a browser.
 *
 * The mutex is why this is a class rather than a bare `onStart { refresh() }`: a screen
 * that collects two flows from the same repository would otherwise fire two identical
 * requests, and the dashboard collects four.
 */
internal class LazyRefresh(private val refresh: suspend () -> Unit) {

    private val mutex = Mutex()
    private var loaded = false

    /** Marks the data stale so the next collector fetches again - call after a write. */
    suspend fun invalidate() = mutex.withLock { loaded = false }

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            // A failed first load must not poison the flow: the screen shows its empty
            // state and the next collection tries again, rather than the tab needing a
            // reload to recover from one dropped request.
            runCatching { refresh() }
                .onSuccess { loaded = true }
                .onFailure { AppLog.w("LazyRefresh", "First load failed: ${it.message}") }
        }
    }
}

/** Loads on first collection. See [LazyRefresh]. */
internal fun <T> Flow<T>.loadingOnce(lazy: LazyRefresh): Flow<T> =
    onStart { lazy.ensureLoaded() }
