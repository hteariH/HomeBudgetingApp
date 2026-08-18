package com.personalmoney.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@JsFun("(k) => { const v = localStorage.getItem(k); return v === null ? null : v; }")
private external fun lsGet(key: String): String?

@JsFun("(k, v) => localStorage.setItem(k, v)")
private external fun lsSet(key: String, value: String)

@JsFun("(k) => localStorage.removeItem(k)")
private external fun lsRemove(key: String)

/**
 * Browser token storage.
 *
 * localStorage is the only durable option a page has, and it is readable by any script on
 * the origin - so this is weaker than the Android and iOS stores by construction, not by
 * oversight. It is the same place the React client keeps its token today, so the exposure
 * is unchanged; shortening access-token lifetime is the lever that actually helps.
 */
class BrowserTokenStore : TokenStore {

    private val _signedIn = MutableStateFlow(lsGet(KEY_ACCESS) != null)
    override val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    override var accessToken: String?
        get() = lsGet(KEY_ACCESS)
        set(value) { put(KEY_ACCESS, value) }

    override var refreshToken: String?
        get() = lsGet(KEY_REFRESH)
        set(value) { put(KEY_REFRESH, value) }

    override var email: String?
        get() = lsGet(KEY_EMAIL)
        set(value) { put(KEY_EMAIL, value) }

    override fun save(access: String, refresh: String, email: String?) {
        lsSet(KEY_ACCESS, access)
        lsSet(KEY_REFRESH, refresh)
        put(KEY_EMAIL, email)
        _signedIn.value = true
    }

    override fun clear() {
        lsRemove(KEY_ACCESS)
        lsRemove(KEY_REFRESH)
        lsRemove(KEY_EMAIL)
        _signedIn.value = false
    }

    private fun put(key: String, value: String?) {
        if (value == null) lsRemove(key) else lsSet(key, value)
    }

    private companion object {
        const val KEY_ACCESS = "pm_access_token"
        const val KEY_REFRESH = "pm_refresh_token"
        const val KEY_EMAIL = "pm_email"
    }
}
