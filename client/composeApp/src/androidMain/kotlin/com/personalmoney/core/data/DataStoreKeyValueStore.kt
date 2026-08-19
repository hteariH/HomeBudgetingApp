package com.personalmoney.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/**
 * The Android [KeyValueStore], on DataStore.
 *
 * The store name and the key strings are the ones the shipped build already uses, so an
 * existing install keeps its settings across this migration - the file being read is the
 * same file.
 */
class DataStoreKeyValueStore(private val context: Context) : KeyValueStore {

    override fun observeString(key: String): Flow<String?> =
        context.dataStore.data.map { it[stringPreferencesKey(key)] }

    override fun observeLong(key: String): Flow<Long?> =
        context.dataStore.data.map { it[longPreferencesKey(key)] }

    override fun observeBoolean(key: String): Flow<Boolean?> =
        context.dataStore.data.map { it[booleanPreferencesKey(key)] }

    override suspend fun putString(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun putLong(key: String, value: Long) {
        context.dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    override suspend fun remove(vararg keys: String) {
        // stringPreferencesKey regardless of the stored type: DataStore's Preferences.Key
        // compares by name alone, the type parameter being a compile-time phantom, so this
        // removes a Long-typed entry just as well.
        context.dataStore.edit { prefs -> keys.forEach { prefs.remove(stringPreferencesKey(it)) } }
    }

    override suspend fun updateLong(key: String, transform: (Long) -> Long): Long {
        var updated = 0L
        // DataStore's edit block is the transaction, so the read and the write cannot be
        // interleaved with another writer.
        context.dataStore.edit { prefs ->
            val k = longPreferencesKey(key)
            updated = transform(prefs[k] ?: 0L)
            prefs[k] = updated
        }
        return updated
    }
}
