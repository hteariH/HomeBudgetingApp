package com.personalmoney.feature.currencies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.database.entity.CurrencyEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CurrencyEditUiState(
    val originalCode: String? = null,
    val code: String = "",
    val name: String = "",
    val symbol: String = "",
    val decimals: Int = 2,
    val active: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val usageBlocked: Int = 0,
    val errorMessage: String? = null
) {
    val isEditing: Boolean get() = originalCode != null
    val canSave: Boolean
        get() = !saving && isCodeValid(code) && name.isNotBlank() && symbol.isNotBlank()
}

private val codeRegex = Regex("^[A-Za-z0-9_]{2,8}$")
private fun isCodeValid(code: String) = codeRegex.matches(code)

@HiltViewModel
class CurrencyEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CurrencyRepository
) : ViewModel() {

    private val initialCode: String? = savedStateHandle.get<String>("code")?.takeIf { it.isNotBlank() }
    private val _state = MutableStateFlow(CurrencyEditUiState(originalCode = initialCode))
    val uiState: StateFlow<CurrencyEditUiState> = _state.asStateFlow()

    init {
        if (initialCode != null) {
            viewModelScope.launch {
                repository.findByCode(initialCode)?.let { c ->
                    _state.value = CurrencyEditUiState(
                        originalCode = c.code,
                        code = c.code,
                        name = c.name,
                        symbol = c.symbol,
                        decimals = c.decimals,
                        active = c.active
                    )
                }
            }
        }
    }

    fun setCode(value: String) = _state.update { it.copy(code = value.uppercase().take(8)) }
    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setSymbol(value: String) = _state.update { it.copy(symbol = value.take(5)) }
    fun setDecimals(value: Int) = _state.update { it.copy(decimals = value.coerceIn(0, 6)) }
    fun setActive(value: Boolean) = _state.update { it.copy(active = value) }
    fun clearError() = _state.update { it.copy(errorMessage = null) }
    fun clearUsageBlocked() = _state.update { it.copy(usageBlocked = 0) }

    fun save() {
        val s = _state.value
        if (!s.canSave) {
            if (!isCodeValid(s.code)) {
                _state.update { it.copy(errorMessage = "INVALID_CODE") }
            }
            return
        }
        _state.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                if (s.originalCode == null) {
                    val existing = repository.findByCode(s.code)
                    if (existing != null) {
                        _state.update { it.copy(saving = false, errorMessage = "CODE_TAKEN") }
                        return@runCatching
                    }
                    repository.create(
                        CurrencyEntity(
                            code = s.code,
                            name = s.name.trim(),
                            symbol = s.symbol.trim(),
                            decimals = s.decimals,
                            active = s.active,
                            sortOrder = 999 // user-added, sort at the end
                        )
                    )
                    _state.update { it.copy(saving = false, saved = true) }
                } else {
                    val existing = repository.findByCode(s.originalCode) ?: return@runCatching
                    repository.update(
                        existing.copy(
                            name = s.name.trim(),
                            symbol = s.symbol.trim(),
                            decimals = s.decimals,
                            active = s.active
                        )
                    )
                    _state.update { it.copy(saving = false, saved = true) }
                }
            }.onFailure { e ->
                _state.update { it.copy(saving = false, errorMessage = e.message ?: "Save failed") }
            }
        }
    }

    fun delete() {
        val code = _state.value.originalCode ?: return
        viewModelScope.launch {
            val uses = repository.usageCount(code)
            if (uses > 0) {
                _state.update { it.copy(usageBlocked = uses) }
                return@launch
            }
            runCatching { repository.delete(code) }
                .onSuccess { _state.update { it.copy(deleted = true) } }
                .onFailure { e -> _state.update { it.copy(errorMessage = e.message) } }
        }
    }
}
