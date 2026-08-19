package com.personalmoney.feature.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which rule applies to which form.
 *
 * The two are not the same rule, and treating them as one is how sign-in broke: the
 * length minimum belongs to `RegisterRequest` on the server, while `LoginRequest` asks
 * only that the password not be blank. Enforcing the stricter one at sign-in locked out
 * every account whose password predates it, with a dead button and nothing to explain it.
 */
class AuthUiStateTest {

    private val short = "short"
    private val long = "a".repeat(AuthUiState.MIN_PASSWORD_LENGTH)

    @Test
    fun `a short password can still sign in`() {
        val state = AuthUiState(email = "user@example.com", password = short)

        // The server accepts it, so the client must not be the one refusing.
        assertTrue(state.canSubmit)
    }

    @Test
    fun `a short password cannot register`() {
        val state = AuthUiState(
            email = "user@example.com",
            password = short,
            firstName = "Ada",
            lastName = "Lovelace",
            registerMode = true,
        )

        // Here the rule is real: the server would answer 400, and catching it in front of
        // the user is the point of checking at all.
        assertFalse(state.canSubmit)
    }

    @Test
    fun `registration still needs both names`() {
        val base = AuthUiState(
            email = "user@example.com",
            password = long,
            registerMode = true,
        )

        assertFalse(base.canSubmit)
        assertFalse(base.copy(firstName = "Ada").canSubmit)
        assertTrue(base.copy(firstName = "Ada", lastName = "Lovelace").canSubmit)
    }

    @Test
    fun `an empty password submits nothing`() {
        val state = AuthUiState(email = "user@example.com", password = "")

        assertFalse(state.canSubmit)
    }

    @Test
    fun `a request already in flight cannot be sent twice`() {
        val state = AuthUiState(email = "user@example.com", password = long, busy = true)

        assertFalse(state.canSubmit)
    }
}
