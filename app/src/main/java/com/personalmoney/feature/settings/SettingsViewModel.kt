package com.personalmoney.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.database.entity.CurrencyEntity
import com.personalmoney.feature.settings.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val baseCurrency: String = "USD",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val currencies: List<CurrencyEntity> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    currencyRepository: CurrencyRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.baseCurrency,
        preferences.themeMode,
        preferences.dynamicColor,
        preferences.biometricLockEnabled,
        currencyRepository.observeAll()
    ) { base, theme, dynamic, biometric, currencies ->
        SettingsUiState(
            baseCurrency = base,
            themeMode = theme,
            dynamicColor = dynamic,
            biometricLockEnabled = biometric,
            currencies = currencies
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

    fun exportTo(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.exportTo(uri)
            result.onSuccess { count -> onDone("Exported $count transactions") }
                .onFailure { e -> onDone(e.message ?: "Export failed") }
        }
    }

    fun importFrom(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.importFrom(uri)
            result.onSuccess { count -> onDone("Imported $count transactions") }
                .onFailure { e -> onDone(e.message ?: "Import failed") }
        }
    }
}
