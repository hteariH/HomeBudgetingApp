package com.personalmoney.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.auth.AuthResult
import com.personalmoney.core.sync.SyncTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * Mirrors the server's rule. Checking here as well turns a round trip and a 400
     * into immediate feedback while the user is still typing.
     */
    val canSubmit: Boolean
        get() = !busy &&
            email.contains('@') &&
            password.length >= MIN_PASSWORD_LENGTH &&
            (!registerMode || (firstName.isNotBlank() && lastName.isNotBlank()))

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
                    _state.update { it.copy(busy = false, signedIn = true) }
                }
                is AuthResult.Failure ->
                    _state.update { it.copy(busy = false, error = result.message) }
            }
        }
    }
}
