package com.personalmoney.core.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Where the access and refresh tokens live.
 *
 * An interface because secure storage is the one part of auth that is genuinely different
 * per platform: EncryptedSharedPreferences on Android, the Keychain on iOS, localStorage in
 * a browser. Everything above it - the Ktor client, refresh, sign-in - is shared.
 *
 * The signed-in state is a flow so the UI can react to a session ending, for instance when
 * the server rejects a refresh.
 */
interface TokenStore {
    val signedIn: StateFlow<Boolean>

    var accessToken: String?
    var refreshToken: String?
    var email: String?

    fun save(access: String, refresh: String, email: String?)
    fun clear()
}
