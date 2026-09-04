package com.personalmoney.feature.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.CsvImportRepository
import com.personalmoney.core.domain.model.CsvColumnMapping
import com.personalmoney.core.domain.model.CsvPreview
import com.personalmoney.core.domain.model.ImportResult
import com.personalmoney.core.network.serverMessage
import com.personalmoney.core.ui.PickedFile
import com.personalmoney.resources.Res
import com.personalmoney.resources.common_request_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** The parts of the column mapping the user may correct. */
enum class CsvMappingField { DATE, DESCRIPTION, AMOUNT, CURRENCY }

data class CsvImportUiState(
    val accountId: Long? = null,
    /** The name only. The bytes stay in the ViewModel; see [CsvImportViewModel]. */
    val fileName: String? = null,
    val preview: CsvPreview? = null,
    /** External id to category, and the selection the confirm sends. */
    val categories: Map<String, Long?> = emptyMap(),
    /**
     * External id to the other account, for rows the user marked as a transfer instead of
     * giving them a category. Kept apart from [categories] because they are answers to the
     * same question with different consequences: one is spending, the other is not.
     */
    val transfers: Map<String, Long?> = emptyMap(),
    val busy: Boolean = false,
    val error: String? = null,
    /** Held rather than formatted, so the screen renders it in the user's language. */
    val result: ImportResult? = null,
    val mappingExpanded: Boolean = false,
) {
    val canRead: Boolean get() = accountId != null && fileName != null && !busy

    val freshCount: Int get() = preview?.fresh?.size ?: 0
}

/**
 * Importing a statement file.
 *
 * Two steps, the same as the bank import, and for the same reason: the user assigns
 * categories in between. What is different is that the server has no copy of the file, so
 * the confirm carries the rows back up.
 *
 * The file's bytes are a plain field rather than part of the state, deliberately. They are
 * needed again every time the mapping changes - correcting a column re-reads the same file
 * - but a `ByteArray` inside a data class brings an `equals` that compares array identity,
 * and state that compares wrongly is the kind of bug that survives every test.
 *
 * Bound only in the web graph; see [CsvImportRepository].
 */
class CsvImportViewModel(private val repository: CsvImportRepository) : ViewModel() {

    private var picked: PickedFile? = null

    private val _state = MutableStateFlow(CsvImportUiState())
    val state: StateFlow<CsvImportUiState> = _state.asStateFlow()

    /** Changing the account drops the preview: those rows were read for the old one. */
    fun setAccount(accountId: Long?) = _state.update {
        it.copy(
            accountId = accountId,
            preview = null,
            categories = emptyMap(),
            transfers = emptyMap(),
            result = null,
        )
    }

    fun setFile(file: PickedFile) {
        picked = file
        _state.update {
            it.copy(
                fileName = file.name,
                preview = null,
                categories = emptyMap(),
                transfers = emptyMap(),
                result = null,
                error = null,
            )
        }
    }

    fun toggleMapping() = _state.update { it.copy(mappingExpanded = !it.mappingExpanded) }

    /**
     * Records one row's choice from the shared dropdown - see [ImportChoice].
     *
     * The two maps are kept exclusive here rather than on the server: a row is either
     * spending in a category or a move between accounts, and leaving a stale category
     * behind would have it reappear the moment the user changed their mind back.
     */
    fun setRowChoice(externalId: String, optionId: Long?) = _state.update {
        it.copy(
            categories = it.categories + (externalId to ImportChoice.categoryIdOf(optionId)),
            transfers = it.transfers + (externalId to ImportChoice.accountIdOf(optionId)),
        )
    }

    /**
     * Reads the file. Passing no mapping asks the server to detect the columns; the guess
     * comes back in the preview and is what the editor then adjusts.
     */
    fun read(mapping: CsvColumnMapping? = null) {
        val accountId = _state.value.accountId ?: return
        val file = picked ?: return
        _state.update { it.copy(busy = true, error = null, result = null) }
        viewModelScope.launch {
            try {
                val preview = repository.preview(accountId, file.name, file.bytes, mapping)
                _state.update {
                    it.copy(
                        busy = false,
                        preview = preview,
                        // The server has no category guess for a CSV row - it has no MCC
                        // to learn from - so these start empty and the user fills them.
                        categories = preview.fresh.associate { row ->
                            row.externalId to row.suggestedCategoryId
                        },
                        transfers = emptyMap(),
                    )
                }
            } catch (e: Exception) {
                // A file that will not parse comes back as a 422 with the parser's own
                // complaint, which says far more than "could not read the file".
                _state.update { it.copy(busy = false, preview = null, error = messageFor(e)) }
            }
        }
    }

    /** Corrects one column and re-reads, because every row depends on the mapping. */
    fun setMappingColumn(field: CsvMappingField, header: String?) {
        val current = _state.value.preview?.mapping ?: return
        val next = when (field) {
            // Required by the parser, so these two never take a null: the editor does not
            // offer "none" for them.
            CsvMappingField.DATE -> current.copy(date = header ?: return)
            CsvMappingField.DESCRIPTION -> current.copy(description = header ?: return)
            CsvMappingField.AMOUNT -> current.copy(amount = header)
            CsvMappingField.CURRENCY -> current.copy(currency = header)
        }
        read(next)
    }

    fun confirm(onImported: () -> Unit) {
        val accountId = _state.value.accountId ?: return
        val preview = _state.value.preview ?: return
        val fresh = preview.fresh
        if (fresh.isEmpty()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val chosen = _state.value.categories
                val transfers = _state.value.transfers
                val result = repository.confirm(
                    accountId = accountId,
                    rows = fresh,
                    categories = fresh.associate { it.externalId to chosen[it.externalId] },
                    // Only the rows actually marked as transfers: a null here would be read
                    // as "no counterpart" for every other row, which is what it means.
                    transfers = fresh.mapNotNull { row ->
                        transfers[row.externalId]?.let { row.externalId to it }
                    }.toMap(),
                )
                // The file is spent: importing the same rows again would only find them
                // already in the ledger, so the panel goes back to asking for a new one.
                picked = null
                _state.update {
                    it.copy(
                        busy = false,
                        fileName = null,
                        preview = null,
                        categories = emptyMap(),
                        transfers = emptyMap(),
                        result = result,
                    )
                }
                onImported()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = messageFor(e)) }
            }
        }
    }

    private suspend fun messageFor(e: Exception): String =
        e.serverMessage() ?: getString(Res.string.common_request_failed)
}
