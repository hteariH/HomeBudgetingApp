package com.personalmoney.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.feature.settings.backup.BackupManager
import com.personalmoney.feature.settings.backup.BackupPreview
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backup and restore, which are Android-only.
 *
 * The format is the Room schema itself - see `BackupData` - so there is nothing here for a
 * client with no database. The Uri in these signatures comes from the storage-access
 * picker and is the reason this cannot be shared code even in principle.
 */
class BackupViewModel(
    private val backupManager: BackupManager,
) : ViewModel() {

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
