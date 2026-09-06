package com.personalmoney.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.auth.AuthResult
import com.personalmoney.core.sync.SyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val registerMode: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false
) {
    /**
     * Mirrors the server's rule - and only where the server actually has one.
     *
     * The length minimum belongs to registration alone (`RegisterRequest` carries
     * `@Size(min = 10)`; `LoginRequest` asks only for `@NotBlank`). Applying it to
     * sign-in as well locked out every account whose password was set before that rule
     * existed, or by any other client: the button simply never enabled, with nothing on
     * screen to say why. A minimum length is a rule about *choosing* a password, never
     * about typing one you already have.
     */
    val canSubmit: Boolean
        get() = !busy && email.contains('@') && if (registerMode) {
            password.length >= MIN_PASSWORD_LENGTH &&
                firstName.isNotBlank() &&
                lastName.isNotBlank()
        } else {
            password.isNotBlank()
        }

    companion object {
        const val MIN_PASSWORD_LENGTH = 10
    }
}

class AuthViewModel constructor(
    private val authRepository: AuthRepository,
    private val syncTrigger: SyncTrigger
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // Empty the form whenever a session ends, because this view model outlives the
        // session: signing out or deleting an account swaps the nav host back to this
        // screen with the same instance, and it was still holding the departing user's
        // name, email and password. On a shared phone the next person arrived at a
        // registration form filled in with somebody else's address.
        //
        // Only transitions count. A StateFlow replays its current value to a new
        // collector, and reacting to that would wipe whatever is being typed the moment
        // the screen appears.
        viewModelScope.launch {
            authRepository.signedIn
                .drop(1)
                .filter { !it }
                .collect { _state.value = AuthUiState() }
        }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onFirstNameChange(value: String) = _state.update { it.copy(firstName = value, error = null) }
    fun onLastNameChange(value: String) = _state.update { it.copy(lastName = value, error = null) }

    fun toggleMode() = _state.update {
        it.copy(registerMode = !it.registerMode, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            val result = if (current.registerMode) {
                authRepository.register(
                    email = current.email,
                    password = current.password,
                    firstName = current.firstName,
                    lastName = current.lastName
                )
            } else {
                authRepository.login(current.email, current.password)
            }

            when (result) {
                is AuthResult.Success -> {
                    // Pull the account's existing data straight away, so a user signing
                    // in on a second device sees their accounts rather than an empty app.
                    syncTrigger.requestSync()
                    // A fresh state rather than a copy: the credentials have done their
                    // job, and there is no reason for a plaintext password to sit in
                    // memory for as long as the app runs.
                    _state.value = AuthUiState(signedIn = true)
                }
                is AuthResult.Failure ->
                    _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }
}
