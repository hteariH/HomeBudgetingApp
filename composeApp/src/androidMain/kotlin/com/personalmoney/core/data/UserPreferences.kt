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

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class UserPreferences constructor(
    private val context: Context
) {
    private object Keys {
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val DEFAULT_ACCOUNT = longPreferencesKey("default_account")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val FX_LAST_REFRESH_MS = longPreferencesKey("fx_last_refresh_ms")
        val TRANSACTION_SAVE_COUNT = longPreferencesKey("transaction_save_count")
        val ADS_CONSENT_OBTAINED = booleanPreferencesKey("ads_consent_obtained")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val NUMPAD_PHONE_LAYOUT = booleanPreferencesKey("numpad_phone_layout")

        // Server-issued cursor for the last successful sync pull. Kept as the exact
        // string the server sent rather than a parsed instant, so no local clock or
        // formatting quirk can shift it and skip changes.
        val SYNC_CURSOR = stringPreferencesKey("sync_cursor")
        val SYNC_LAST_OK_MS = longPreferencesKey("sync_last_ok_ms")
    }

    val baseCurrency: Flow<String> = context.dataStore.data.map { it[Keys.BASE_CURRENCY] ?: "USD" }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }
    val language: Flow<String?> = context.dataStore.data.map { it[Keys.LANGUAGE] }
    val defaultAccountId: Flow<Long?> = context.dataStore.data.map { it[Keys.DEFAULT_ACCOUNT] }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }
    val fxLastRefreshMs: Flow<Long> = context.dataStore.data.map { it[Keys.FX_LAST_REFRESH_MS] ?: 0L }
    val transactionSaveCount: Flow<Long> = context.dataStore.data.map { it[Keys.TRANSACTION_SAVE_COUNT] ?: 0L }
    val adsConsentObtained: Flow<Boolean> = context.dataStore.data.map { it[Keys.ADS_CONSENT_OBTAINED] ?: false }
    val biometricLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_LOCK_ENABLED] ?: false }
    val numpadPhoneLayout: Flow<Boolean> = context.dataStore.data.map { it[Keys.NUMPAD_PHONE_LAYOUT] ?: false }
    val syncCursor: Flow<String> = context.dataStore.data.map { it[Keys.SYNC_CURSOR] ?: "" }
    val syncLastOkMs: Flow<Long> = context.dataStore.data.map { it[Keys.SYNC_LAST_OK_MS] ?: 0L }

    suspend fun setSyncCursor(cursor: String) {
        context.dataStore.edit { it[Keys.SYNC_CURSOR] = cursor }
    }

    suspend fun setSyncLastOkMs(ms: Long) {
        context.dataStore.edit { it[Keys.SYNC_LAST_OK_MS] = ms }
    }

    /** Wipes sync bookkeeping on sign-out so the next account starts from scratch. */
    suspend fun clearSyncState() {
        context.dataStore.edit {
            it.remove(Keys.SYNC_CURSOR)
            it.remove(Keys.SYNC_LAST_OK_MS)
        }
    }

    suspend fun setBaseCurrency(code: String) {
        context.dataStore.edit { it[Keys.BASE_CURRENCY] = code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setLanguage(tag: String?) {
        context.dataStore.edit {
            if (tag == null) it.remove(Keys.LANGUAGE) else it[Keys.LANGUAGE] = tag
        }
    }

    suspend fun setDefaultAccount(id: Long?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.DEFAULT_ACCOUNT) else it[Keys.DEFAULT_ACCOUNT] = id
        }
    }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDED] = value }
    }

    suspend fun setDynamicColor(value: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
    }

    suspend fun setFxLastRefresh(ms: Long) {
        context.dataStore.edit { it[Keys.FX_LAST_REFRESH_MS] = ms }
    }

    suspend fun incrementTransactionSaveCount(): Long {
        var newValue = 0L
        context.dataStore.edit {
            newValue = (it[Keys.TRANSACTION_SAVE_COUNT] ?: 0L) + 1L
            it[Keys.TRANSACTION_SAVE_COUNT] = newValue
        }
        return newValue
    }

    suspend fun setAdsConsentObtained(value: Boolean) {
        context.dataStore.edit { it[Keys.ADS_CONSENT_OBTAINED] = value }
    }

    suspend fun setBiometricLockEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = value }
    }

    suspend fun setNumpadPhoneLayout(value: Boolean) {
        context.dataStore.edit { it[Keys.NUMPAD_PHONE_LAYOUT] = value }
    }
}
