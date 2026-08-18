package com.personalmoney.core.data

import kotlinx.coroutines.flow.Flow

/**
 * The small amount of persistence that is not the database: settings, and bookkeeping the
 * app needs before it has read anything.
 *
 * An interface with a platform implementation rather than one library used everywhere,
 * for the same reason [com.personalmoney.core.auth.TokenStore] is: what backs it differs
 * completely. Android has DataStore, which is transactional and file-backed and reads
 * asynchronously; a browser has `localStorage`, which is a synchronous string map. The
 * shape they have in common is exactly this - read a key as a flow, write a key - and
 * [UserPreferences] builds everything the app actually asks for on top of it.
 *
 * Reads return a flow rather than a value because a preference change has to reach the
 * screens already showing: switching the base currency re-renders every amount on screen.
 */
interface KeyValueStore {

    fun observeString(key: String): Flow<String?>

    fun observeLong(key: String): Flow<Long?>

    fun observeBoolean(key: String): Flow<Boolean?>

    suspend fun putString(key: String, value: String)

    suspend fun putLong(key: String, value: Long)

    suspend fun putBoolean(key: String, value: Boolean)

    suspend fun remove(vararg keys: String)

    /**
     * Reads, applies [transform] and writes back, without another writer slipping in
     * between. Only counters need it, but a counter that loses increments is not a
     * counter.
     */
    suspend fun updateLong(key: String, transform: (Long) -> Long): Long
}
