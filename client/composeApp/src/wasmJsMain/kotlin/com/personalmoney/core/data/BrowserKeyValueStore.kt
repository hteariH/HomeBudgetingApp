package com.personalmoney.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

@JsFun("(k) => { const v = localStorage.getItem(k); return v === null ? null : v; }")
private external fun lsGet(key: String): String?

@JsFun("(k, v) => localStorage.setItem(k, v)")
private external fun lsSet(key: String, value: String)

@JsFun("(k) => localStorage.removeItem(k)")
private external fun lsRemove(key: String)

/**
 * The browser [KeyValueStore], on localStorage. Same store the tokens use - see
 * [com.personalmoney.core.auth.BrowserTokenStore] for why that is the only durable option
 * a page has.
 *
 * localStorage has no change notification worth using (`storage` events fire in *other*
 * tabs, not the one that wrote), so the flows are fed by a revision counter this class
 * bumps on every write. That makes a preference change reach the screens in this tab,
 * which is what the app needs; a second tab will not see it until it reloads. Everything
 * is a string on the wire, because localStorage has no other type.
 */
class BrowserKeyValueStore : KeyValueStore {

    /** Bumped on every write; the flows re-read localStorage whenever it changes. */
    private val revision = MutableStateFlow(0)

    override fun observeString(key: String): Flow<String?> = revision.map { lsGet(key) }

    override fun observeLong(key: String): Flow<Long?> = revision.map { lsGet(key)?.toLongOrNull() }

    override fun observeBoolean(key: String): Flow<Boolean?> =
        revision.map { lsGet(key)?.toBooleanStrictOrNull() }

    override suspend fun putString(key: String, value: String) = write { lsSet(key, value) }

    override suspend fun putLong(key: String, value: Long) = write { lsSet(key, value.toString()) }

    override suspend fun putBoolean(key: String, value: Boolean) =
        write { lsSet(key, value.toString()) }

    override suspend fun remove(vararg keys: String) = write { keys.forEach { lsRemove(it) } }

    /**
     * Not atomic, and it does not need to be: a page is single-threaded, so nothing can run
     * between the read and the write.
     */
    override suspend fun updateLong(key: String, transform: (Long) -> Long): Long {
        val updated = transform(lsGet(key)?.toLongOrNull() ?: 0L)
        write { lsSet(key, updated.toString()) }
        return updated
    }

    private inline fun write(block: () -> Unit) {
        block()
        revision.value += 1
    }
}
