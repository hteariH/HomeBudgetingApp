package com.personalmoney.feature.homemoney

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.R
import com.personalmoney.core.homemoney.HomeMoneyException
import com.personalmoney.core.homemoney.HomeMoneyRepository
import com.personalmoney.core.homemoney.ImportCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ImportStep { CREDENTIALS, PREVIEW, DONE }

/** A candidate plus its current checkbox state in the preview list. */
data class CandidateRow(
    val candidate: ImportCandidate,
    val selected: Boolean,
)

data class HomeMoneyImportUiState(
    val step: ImportStep = ImportStep.CREDENTIALS,
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val accountHolder: String? = null,
    val rows: List<CandidateRow> = emptyList(),
    val importedCount: Int = 0,
) {
    val selectedCount: Int get() = rows.count { it.selected }
    val canConnect: Boolean get() = username.isNotBlank() && password.isNotBlank() && !loading
    val canImport: Boolean get() = selectedCount > 0 && !loading
}

@HiltViewModel
class HomeMoneyImportViewModel @Inject constructor(
    private val repository: HomeMoneyRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeMoneyImportUiState())
    val state: StateFlow<HomeMoneyImportUiState> = _state.asStateFlow()

    private var token: String? = null

    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun clearError() = _state.update { it.copy(error = null) }

    /** Log in, fetch the balance tree, and move to the preview step. */
    fun connect() {
        val s = _state.value
        if (!s.canConnect) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val t = repository.login(s.username, s.password)
                token = t
                repository.fetchCandidates(t)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        loading = false,
                        step = ImportStep.PREVIEW,
                        accountHolder = result.accountHolder,
                        rows = result.candidates.map { c -> CandidateRow(c, c.defaultSelected) },
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = messageFor(e)) }
            }
        }
    }

    fun toggle(index: Int) = _state.update { st ->
        val rows = st.rows.toMutableList()
        rows.getOrNull(index)?.let { row -> rows[index] = row.copy(selected = !row.selected) }
        st.copy(rows = rows)
    }

    fun setAllSelected(selected: Boolean) = _state.update { st ->
        st.copy(rows = st.rows.map { it.copy(selected = selected) })
    }

    /** Create local accounts for the checked rows and move to the done step. */
    fun import() {
        val s = _state.value
        if (!s.canImport) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.import(s.rows.filter { it.selected }.map { it.candidate })
            }.onSuccess { count ->
                _state.update { it.copy(loading = false, step = ImportStep.DONE, importedCount = count) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = messageFor(e)) }
            }
        }
    }

    /** Return to the credentials step (e.g. to pick different accounts); keeps the entered login. */
    fun backToCredentials() {
        token = null
        _state.update { HomeMoneyImportUiState(username = it.username, password = it.password) }
    }

    private fun messageFor(e: Throwable): String = when (e) {
        is HomeMoneyException -> e.serverMessage?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.homemoney_error_generic)
        else -> appContext.getString(R.string.homemoney_error_network)
    }
}
