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
    val biometricLockEnabled: Flow<Boolean> =
        store.observeBoolean(Keys.BIOMETRIC_LOCK_ENABLED).map { it ?: false }
    /**
     * The OAuth state minted when the user set off for homemoney.ua's consent screen.
     *
     * Stored rather than held in memory because the round trip leaves the page: the app is
     * reloaded from scratch on the way back, and homemoney.ua returns no state of its own,
     * so this is the only thing tying the returning code to the browser that asked for it.
     * Cleared the moment it is spent.
     */
    val homeMoneyAuthState: Flow<String?> = store.observeString(Keys.HOMEMONEY_AUTH_STATE)

    val syncCursor: Flow<String> = store.observeString(Keys.SYNC_CURSOR).map { it ?: "" }
    val syncLastOkMs: Flow<Long> = store.observeLong(Keys.SYNC_LAST_OK_MS).map { it ?: 0L }

    /**
     * Whether the one-off full re-pull that fills in transaction authors (schema v5) has
     * been dealt with. Account-scoped: a wipe resets the cursor, so the next sync is a
     * full pull anyway and just has to record that.
     */
    val authorBackfillDone: Flow<Boolean> =
        store.observeBoolean(Keys.AUTHOR_BACKFILL_DONE).map { it ?: false }

    /**
     * The household this phone's data was last synced for. When the server says the account
     * now belongs to another one, the local ledger is somebody else's and gets cleared.
     * Account-scoped, and written again by the sync engine right after the wipe it causes.
     */
    val householdId: Flow<Long?> = store.observeLong(Keys.HOUSEHOLD_ID)

    /**
     * The name of the household the phone has just switched to, kept until the dashboard
     * has told the user once. Account-scoped like [householdId].
     */
    val householdNotice: Flow<String?> = store.observeString(Keys.HOUSEHOLD_NOTICE)

    suspend fun setBaseCurrency(code: String) = store.putString(Keys.BASE_CURRENCY, code)

    suspend fun setThemeMode(mode: ThemeMode) = store.putString(Keys.THEME, mode.name)

    suspend fun setLanguage(tag: String?) =
        if (tag == null) store.remove(Keys.LANGUAGE) else store.putString(Keys.LANGUAGE, tag)

    suspend fun setDefaultAccount(id: Long?) =
        if (id == null) store.remove(Keys.DEFAULT_ACCOUNT) else store.putLong(Keys.DEFAULT_ACCOUNT, id)

    suspend fun setOnboarded(value: Boolean) = store.putBoolean(Keys.ONBOARDED, value)

    suspend fun setDynamicColor(value: Boolean) = store.putBoolean(Keys.DYNAMIC_COLOR, value)

    suspend fun setFxLastRefresh(ms: Long) = store.putLong(Keys.FX_LAST_REFRESH_MS, ms)

    suspend fun setBiometricLockEnabled(value: Boolean) =
        store.putBoolean(Keys.BIOMETRIC_LOCK_ENABLED, value)


    suspend fun setHomeMoneyAuthState(state: String?) =
        if (state == null) store.remove(Keys.HOMEMONEY_AUTH_STATE)
        else store.putString(Keys.HOMEMONEY_AUTH_STATE, state)

    suspend fun setSyncCursor(cursor: String) = store.putString(Keys.SYNC_CURSOR, cursor)

    suspend fun setSyncLastOkMs(ms: Long) = store.putLong(Keys.SYNC_LAST_OK_MS, ms)

    suspend fun setAuthorBackfillDone(value: Boolean) = store.putBoolean(Keys.AUTHOR_BACKFILL_DONE, value)

    suspend fun setHouseholdId(id: Long) = store.putLong(Keys.HOUSEHOLD_ID, id)

    suspend fun setHouseholdNotice(name: String?) =
        if (name == null) store.remove(Keys.HOUSEHOLD_NOTICE) else store.putString(Keys.HOUSEHOLD_NOTICE, name)

    /**
     * Drops every preference that belongs to the account rather than to the device, so the
     * next sign-in on this phone starts clean.
     *
     * What goes: sync bookkeeping, because the next account's history has nothing to do
     * with this cursor; the default account and the base currency, which name rows and
     * settings that are about to stop existing; and any half-finished HomeMoney consent.
     *
     * What stays: theme, language, dynamic colour and the biometric lock. Those describe
     * this device and this phone's owner, not the account - resetting someone's dark mode
     * because they signed out would be a strange thing to do.
     */
    suspend fun clearAccountScoped() = store.remove(
        Keys.SYNC_CURSOR,
        Keys.SYNC_LAST_OK_MS,
        Keys.AUTHOR_BACKFILL_DONE,
        Keys.HOUSEHOLD_ID,
        Keys.HOUSEHOLD_NOTICE,
        Keys.DEFAULT_ACCOUNT,
        Keys.BASE_CURRENCY,
        Keys.HOMEMONEY_AUTH_STATE,
        // Left by builds that sold premium.
        "premium", "premium_expires_at_ms",
    )
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
    const val BIOMETRIC_LOCK_ENABLED = "biometric_lock_enabled"

    // Outstanding HomeMoney OAuth state, written before leaving for their consent screen
    // and read once on the way back. Web-only in practice, like the import itself.
    const val HOMEMONEY_AUTH_STATE = "homemoney_auth_state"

    // Server-issued cursor for the last successful sync pull. Kept as the exact string
    // the server sent rather than a parsed instant, so no local clock or formatting quirk
    // can shift it and skip changes.
    const val SYNC_CURSOR = "sync_cursor"
    const val SYNC_LAST_OK_MS = "sync_last_ok_ms"

    // Set once the full re-pull that gave pre-v5 transactions their author has been
    // started (or was unnecessary because the phone had never synced).
    const val AUTHOR_BACKFILL_DONE = "author_backfill_done"

    // The household the local ledger belongs to, and the one-time "you are now in ..." notice
    // left for the dashboard when it changed.
    const val HOUSEHOLD_ID = "household_id"
    const val HOUSEHOLD_NOTICE = "household_notice"
}
