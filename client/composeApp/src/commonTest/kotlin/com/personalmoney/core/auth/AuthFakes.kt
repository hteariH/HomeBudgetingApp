package com.personalmoney.core.auth

import com.personalmoney.core.data.LocalDataWiper
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.createJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A token store that remembers what was done to it, with no platform underneath. */
class FakeTokenStore(signedIn: Boolean = true) : TokenStore {
    private val _signedIn = MutableStateFlow(signedIn)
    override val signedIn: StateFlow<Boolean> = _signedIn
    override var accessToken: String? = if (signedIn) "access" else null
    override var refreshToken: String? = if (signedIn) "refresh" else null
    override var email: String? = if (signedIn) "someone@example.com" else null
    override var serverUrl: String? = null
    override var lastAccount: String? = null
    var cleared = false

    override fun save(access: String, refresh: String, email: String?) {
        accessToken = access
        refreshToken = refresh
        this.email = email
        _signedIn.value = true
    }

    override fun clear() {
        cleared = true
        accessToken = null
        refreshToken = null
        email = null
        _signedIn.value = false
    }
}

/** Counts wipes instead of performing one. */
class RecordingWiper : LocalDataWiper {
    var wipes = 0
    override suspend fun wipe() {
        wipes++
    }
}

/**
 * A real [AuthRepository] over a mock transport, which is the point: the behaviour under
 * test is how it sequences the API, the local wipe and the token store, and a fake
 * repository would only assert about itself.
 */
fun authRepositoryOver(
    engine: MockEngine,
    tokens: TokenStore,
    wiper: LocalDataWiper,
) = AuthRepository(
    HomeBudgetApi(
        HttpClient(engine) {
            // The same two settings the production clients are built with. Negotiation is
            // not optional here: sign-in reads a body, and without it the call fails on
            // "no transformation found" rather than on anything the test is about.
            expectSuccess = true
            install(ContentNegotiation) { json(createJson()) }
        }
    ),
    tokens,
    wiper,
)
