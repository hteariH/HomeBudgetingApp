package com.personalmoney.core.data

import com.personalmoney.core.data.PreferenceKeys as Keys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Every user setting, and the defaults that apply when one has never been written.
 *
 * Shared code: the keys, the defaults and the fallbacks are the product's behaviour, not
 * the platform's, and they were worth writing once. [KeyValueStore] is the only part that
 * differs by platform.
 */
class UserPreferences(private val store: KeyValueStore) {

    val baseCurrency: Flow<String> = store.observeString(Keys.BASE_CURRENCY).map { it ?: "USD" }

    /** An unrecognised stored value falls back rather than throwing - a bad write here would otherwise brick the app on launch. */
    val themeMode: Flow<ThemeMode> = store.observeString(Keys.THEME).map { stored ->
        runCatching { ThemeMode.valueOf(stored ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    /** Null means "follow the system", which is not the same as English. */
    val language: Flow<String?> = store.observeString(Keys.LANGUAGE)
    val defaultAccountId: Flow<Long?> = store.observeLong(Keys.DEFAULT_ACCOUNT)
    val onboarded: Flow<Boolean> = store.observeBoolean(Keys.ONBOARDED).map { it ?: false }
    /**
     * Off unless the user asks for it.
     *
     * Material You repaints the app from the phone's wallpaper, which undoes the palette
     * the web and Android clients now share - and the point of one codebase is that they
     * look like one product. Anyone who preferred their wallpaper's colours can still turn
     * it back on; it just no longer wins by default.
     */
    val dynamicColor: Flow<Boolean> = store.observeBoolean(Keys.DYNAMIC_COLOR).map { it ?: false }
    val fxLastRefreshMs: Flow<Long> = store.observeLong(Keys.FX_LAST_REFRESH_MS).map { it ?: 0L }
    val transactionSaveCount: Flow<Long> =
        store.observeLong(Keys.TRANSACTION_SAVE_COUNT).map { it ?: 0L }
    val adsConsentObtained: Flow<Boolean> =
        store.observeBoolean(Keys.ADS_CONSENT_OBTAINED).map { it ?: false }
    val biometricLockEnabled: Flow<Boolean> =
        store.observeBoolean(Keys.BIOMETRIC_LOCK_ENABLED).map { it ?: false }
    val numpadPhoneLayout: Flow<Boolean> =
        store.observeBoolean(Keys.NUMPAD_PHONE_LAYOUT).map { it ?: false }
    val syncCursor: Flow<String> = store.observeString(Keys.SYNC_CURSOR).map { it ?: "" }
    val syncLastOkMs: Flow<Long> = store.observeLong(Keys.SYNC_LAST_OK_MS).map { it ?: 0L }

    suspend fun setBaseCurrency(code: String) = store.putString(Keys.BASE_CURRENCY, code)

    suspend fun setThemeMode(mode: ThemeMode) = store.putString(Keys.THEME, mode.name)

    suspend fun setLanguage(tag: String?) =
        if (tag == null) store.remove(Keys.LANGUAGE) else store.putString(Keys.LANGUAGE, tag)

    suspend fun setDefaultAccount(id: Long?) =
        if (id == null) store.remove(Keys.DEFAULT_ACCOUNT) else store.putLong(Keys.DEFAULT_ACCOUNT, id)

    suspend fun setOnboarded(value: Boolean) = store.putBoolean(Keys.ONBOARDED, value)

    suspend fun setDynamicColor(value: Boolean) = store.putBoolean(Keys.DYNAMIC_COLOR, value)

    suspend fun setFxLastRefresh(ms: Long) = store.putLong(Keys.FX_LAST_REFRESH_MS, ms)

    suspend fun incrementTransactionSaveCount(): Long =
        store.updateLong(Keys.TRANSACTION_SAVE_COUNT) { it + 1L }

    suspend fun setAdsConsentObtained(value: Boolean) =
        store.putBoolean(Keys.ADS_CONSENT_OBTAINED, value)

    suspend fun setBiometricLockEnabled(value: Boolean) =
        store.putBoolean(Keys.BIOMETRIC_LOCK_ENABLED, value)

    suspend fun setNumpadPhoneLayout(value: Boolean) =
        store.putBoolean(Keys.NUMPAD_PHONE_LAYOUT, value)

    suspend fun setSyncCursor(cursor: String) = store.putString(Keys.SYNC_CURSOR, cursor)

    suspend fun setSyncLastOkMs(ms: Long) = store.putLong(Keys.SYNC_LAST_OK_MS, ms)

    /** Wipes sync bookkeeping on sign-out so the next account starts from scratch. */
    suspend fun clearSyncState() = store.remove(Keys.SYNC_CURSOR, Keys.SYNC_LAST_OK_MS)
}

/**
 * The stored key names, spelled exactly as the shipped Android build wrote them into
 * DataStore. Changing one silently resets that setting for every existing user.
 */
internal object PreferenceKeys {
    const val BASE_CURRENCY = "base_currency"
    const val THEME = "theme"
    const val LANGUAGE = "language"
    const val DEFAULT_ACCOUNT = "default_account"
    const val ONBOARDED = "onboarded"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val FX_LAST_REFRESH_MS = "fx_last_refresh_ms"
    const val TRANSACTION_SAVE_COUNT = "transaction_save_count"
    const val ADS_CONSENT_OBTAINED = "ads_consent_obtained"
    const val BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"
    const val NUMPAD_PHONE_LAYOUT = "numpad_phone_layout"

    // Server-issued cursor for the last successful sync pull. Kept as the exact string
    // the server sent rather than a parsed instant, so no local clock or formatting quirk
    // can shift it and skip changes.
    const val SYNC_CURSOR = "sync_cursor"
    const val SYNC_LAST_OK_MS = "sync_last_ok_ms"
}
