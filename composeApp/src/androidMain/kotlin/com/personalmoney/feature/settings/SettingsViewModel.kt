package com.personalmoney.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.domain.model.Currency
import com.personalmoney.feature.settings.backup.BackupManager
import com.personalmoney.feature.settings.backup.BackupPreview
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.getString
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

class SettingsViewModel constructor(
    private val preferences: UserPreferences,
    currencyRepository: CurrencyRepository,
    private val backupManager: BackupManager
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

    fun exportTo(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.exportTo(uri)
            result.onSuccess { count -> onDone(getString(Res.string.settings_export_done, count)) }
                .onFailure { onDone(getString(Res.string.settings_export_failed)) }
        }
    }

    fun exportCsv(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            backupManager.exportCsvTo(uri)
                .onSuccess { count -> onDone(getString(Res.string.settings_export_csv_done, count)) }
                .onFailure { onDone(getString(Res.string.settings_export_failed)) }
        }
    }

    private val _importRequest = MutableStateFlow<ImportRequest?>(null)
    val importRequest: StateFlow<ImportRequest?> = _importRequest.asStateFlow()

    /** Read the chosen backup and stage a confirmation; does not modify data yet. */
    fun requestImport(uri: Uri, onError: (String) -> Unit) {
        viewModelScope.launch {
            backupManager.peek(uri)
                .onSuccess { preview -> _importRequest.value = ImportRequest(uri, preview) }
                .onFailure { onError(getString(Res.string.settings_backup_read_failed)) }
        }
    }

    fun cancelImport() { _importRequest.value = null }

    /** Perform the staged import (replaces all data). */
    fun confirmImport(onDone: (String) -> Unit) {
        val request = _importRequest.value ?: return
        _importRequest.value = null
        viewModelScope.launch {
            backupManager.importFrom(request.uri)
                .onSuccess { p -> onDone(getString(Res.string.settings_import_done, p.transactions)) }
                .onFailure { onDone(getString(Res.string.settings_import_failed)) }
        }
    }
}

data class ImportRequest(val uri: Uri, val preview: BackupPreview)
