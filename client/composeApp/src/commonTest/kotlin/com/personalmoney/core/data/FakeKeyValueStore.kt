package com.personalmoney.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** A [KeyValueStore] that lives in memory, so preference behaviour can be tested with the real [UserPreferences]. */
class FakeKeyValueStore : KeyValueStore {
    private val values = MutableStateFlow<Map<String, Any>>(emptyMap())

    private inline fun <reified T> observe(key: String): Flow<T?> = values.map { it[key] as? T }

    override fun observeString(key: String): Flow<String?> = observe(key)
    override fun observeLong(key: String): Flow<Long?> = observe(key)
    override fun observeBoolean(key: String): Flow<Boolean?> = observe(key)

    override suspend fun putString(key: String, value: String) { values.value = values.value + (key to value) }
    override suspend fun putLong(key: String, value: Long) { values.value = values.value + (key to value) }
    override suspend fun putBoolean(key: String, value: Boolean) { values.value = values.value + (key to value) }
    override suspend fun remove(vararg keys: String) { values.value = values.value - keys.toSet() }

    override suspend fun updateLong(key: String, transform: (Long) -> Long): Long {
        val next = transform(values.value[key] as? Long ?: 0L)
        putLong(key, next)
        return next
    }
}
