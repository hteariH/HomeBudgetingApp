package com.personalmoney.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the access and refresh tokens live.
 *
 * [EncryptedSharedPreferences] rather than DataStore: a refresh token is a 60-day
 * credential for somebody's finances, and the app's existing DataStore holds display
 * preferences in plain text. On a rooted or backed-up device that difference matters.
 *
 * The signed-in state is exposed as a flow so the UI can react to a session ending -
 * for instance when the server rejects a refresh.
 */
class AndroidTokenStore(
    private val context: Context
) : TokenStore {
    private val prefs: SharedPreferences by lazy { openPrefs() }

    private val _signedIn = MutableStateFlow(false)
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    init {
        _signedIn.value = accessToken != null
    }

    override var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    override var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    override var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    override fun save(access: String, refresh: String, email: String?) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_EMAIL, email)
            .apply()
        _signedIn.value = true
    }

    override fun clear() {
        prefs.edit().clear().apply()
        _signedIn.value = false
    }

    private fun openPrefs(): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // The keystore entry can become unusable - a restored backup, or a device
        // whose lock screen was removed. Starting over means signing in again, which
        // is far better than the app refusing to launch.
        Log.w(TAG, "Encrypted preferences unusable, resetting the session", e)
        context.deleteSharedPreferences(FILE_NAME)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private companion object {
        const val TAG = "AndroidTokenStore"
        const val FILE_NAME = "auth_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EMAIL = "email"
    }
}
