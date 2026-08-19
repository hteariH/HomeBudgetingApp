package com.personalmoney.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.domain.model.Currency
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
    val dynamicColor: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val numpadPhoneLayout: Boolean = false,
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
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(preferences.baseCurrency, preferences.themeMode, preferences.dynamicColor) { base, theme, dynamic ->
            Triple(base, theme, dynamic)
        },
        combine(preferences.biometricLockEnabled, preferences.numpadPhoneLayout, currencyRepository.observeAll()) { biometric, phoneLayout, currencies ->
            Triple(biometric, phoneLayout, currencies)
        }
    ) { abc, def ->
        SettingsUiState(
            baseCurrency = abc.first,
            themeMode = abc.second,
            dynamicColor = abc.third,
            biometricLockEnabled = def.first,
            numpadPhoneLayout = def.second,
            currencies = def.third
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

    fun setNumpadPhoneLayout(value: Boolean) {
        viewModelScope.launch { preferences.setNumpadPhoneLayout(value) }
    }
}
