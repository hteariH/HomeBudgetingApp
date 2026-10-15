package com.personalmoney.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.auth.AuthRepository
import com.personalmoney.core.auth.AuthResult
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.domain.model.Currency
import com.personalmoney.core.receipt.ReceiptScanner
import com.personalmoney.core.receipt.ScanAvailability
import com.personalmoney.core.receipt.receiptScanAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseCurrency: String = "USD",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val currencies: List<Currency> = emptyList(),
    val message: String? = null
)

/**
 * Everything on the Settings screen that every platform has.
 *
 * Backup and restore are deliberately not here - see [BackupViewModel]. Splitting them
 * kept this one free of file handles, which is what let it move into shared code, and it
 * was a fair split anyway: reading a preference and writing a backup file have nothing in
 * common but the screen they appear on.
 */
class SettingsViewModel(
    private val preferences: UserPreferences,
    private val auth: AuthRepository,
    currencyRepository: CurrencyRepository,
    receiptScanner: ReceiptScanner,
) : ViewModel() {

    private val _receiptSetupNeeded = MutableStateFlow(false)

    /**
     * The household has no receipt reader, so quick add hides its scan button. Provider
     * settings are web-only; this is where the phone says so. Only on a clear "none" from
     * the server - not when the server could not be asked.
     */
    val receiptSetupNeeded: StateFlow<Boolean> = _receiptSetupNeeded.asStateFlow()

    init {
        if (receiptScanAvailable) viewModelScope.launch {
            _receiptSetupNeeded.value = receiptScanner.availability() == ScanAvailability.NOT_SET_UP
        }
    }

    private val _deletion = MutableStateFlow(AccountDeletionState())

    /**
     * Separate from [uiState] rather than folded into it: that one is derived from
     * preference flows and is rebuilt whenever any of them emits, which would drop a
     * transient error the moment anything else changed.
     */
    val deletion: StateFlow<AccountDeletionState> = _deletion.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(preferences.baseCurrency, preferences.themeMode, preferences.dynamicColor) { base, theme, dynamic ->
            Triple(base, theme, dynamic)
        },
        combine(preferences.biometricLockEnabled, currencyRepository.observeAll()) { biometric, currencies ->
            biometric to currencies
        }
    ) { abc, def ->
        SettingsUiState(
            baseCurrency = abc.first,
            themeMode = abc.second,
            dynamicColor = abc.third,
            biometricLockEnabled = def.first,
            currencies = def.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setBaseCurrency(code: String) {
        viewModelScope.launch { preferences.setBaseCurrency(code) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(value) }
    }

    fun setBiometricLockEnabled(value: Boolean) {
        viewModelScope.launch { preferences.setBiometricLockEnabled(value) }
    }

    fun confirmDeleteAccount() {
        _deletion.value = AccountDeletionState(confirming = true)
    }

    fun dismissDeleteAccount() {
        // Only while idle: dismissing mid-request would put the confirmation dialog away
        // while the delete is still in flight, and the next thing the user sees would be
        // the sign-in screen with no explanation.
        if (!_deletion.value.inProgress) _deletion.value = AccountDeletionState()
    }

    /**
     * There is no success branch to handle: a deleted account clears the tokens, which
     * flips `signedIn` and swaps the whole nav host for the sign-in screen. Only the
     * failure is ours to report, and it leaves the dialog open so the user can retry.
     */
    fun deleteAccount() {
        if (_deletion.value.inProgress) return
        _deletion.value = AccountDeletionState(confirming = true, inProgress = true)
        viewModelScope.launch {
            when (val result = auth.deleteAccount()) {
                is AuthResult.Success -> Unit
                is AuthResult.Failure ->
                    _deletion.value = AccountDeletionState(confirming = true, error = result.message)
            }
        }
    }

    fun clearDeleteError() {
        _deletion.value = _deletion.value.copy(error = null)
    }
}

/**
 * Where the "delete my account" flow has got to: nothing, asking, working, or failed.
 */
data class AccountDeletionState(
    val confirming: Boolean = false,
    val inProgress: Boolean = false,
    val error: String? = null,
)
