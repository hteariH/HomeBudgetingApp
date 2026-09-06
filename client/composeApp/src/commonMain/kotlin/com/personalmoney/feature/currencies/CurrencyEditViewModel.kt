package com.personalmoney.feature.currencies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.FxPegs
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.data.ExchangeRateRepository
import com.personalmoney.core.domain.model.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CurrencyEditUiState(
    val originalCode: String? = null,
    val code: String = "",
    val name: String = "",
    val symbol: String = "",
    val decimals: Int = 2,
    val active: Boolean = true,
    /** "1 of this currency is n USD", as typed. Blank means "use the daily rate". */
    val handRate: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val usageBlocked: Int = 0,
    val errorMessage: String? = null
) {
    val isEditing: Boolean get() = originalCode != null

    /**
     * A rate is worth asking for unless the currency already has one that cannot be wrong:
     * the hub is 1 by definition, and a dollar stablecoin is pegged (see [FxPegs]).
     */
    val handRateOffered: Boolean
        get() = code.isNotBlank() && code != FxPegs.HUB && !FxPegs.isPegged(code)

    val handRateValid: Boolean
        get() = handRate.isBlank() || parseRate(handRate) != null

    val canSave: Boolean
        get() = !saving && isCodeValid(code) && name.isNotBlank() && symbol.isNotBlank() && handRateValid
}

/** Accepts the comma a Ukrainian or Russian keyboard puts under the thumb. */
internal fun parseRate(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

/** 1.0 reads better as "1"; anything else keeps every digit the user might have typed. */
internal fun formatRate(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private val codeRegex = Regex("^[A-Za-z0-9_]{2,8}$")
private fun isCodeValid(code: String) = codeRegex.matches(code)

class CurrencyEditViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CurrencyRepository,
    private val exchangeRates: ExchangeRateRepository
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
                        active = c.active,
                        handRate = exchangeRates.handRate(c.code)?.let(::formatRate).orEmpty()
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
    fun setHandRate(value: String) = _state.update { it.copy(handRate = value) }
    fun clearError() = _state.update { it.copy(errorMessage = null) }
    fun clearUsageBlocked() = _state.update { it.copy(usageBlocked = 0) }

    fun save() {
        val s = _state.value
        if (!s.canSave) {
            if (!isCodeValid(s.code)) {
                _state.update { it.copy(errorMessage = "INVALID_CODE") }
            } else if (!s.handRateValid) {
                _state.update { it.copy(errorMessage = "INVALID_RATE") }
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
                        Currency(
                            code = s.code,
                            name = s.name.trim(),
                            symbol = s.symbol.trim(),
                            decimals = s.decimals,
                            active = s.active,
                            sortOrder = 999 // user-added, sort at the end
                        )
                    )
                    saveHandRate(s)
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
                    saveHandRate(s)
                    _state.update { it.copy(saving = false, saved = true) }
                }
            }.onFailure { e ->
                _state.update { it.copy(saving = false, errorMessage = e.message ?: "Save failed") }
            }
        }
    }

    /**
     * Writes the rate the user typed, or clears it when they emptied the field.
     *
     * Only for currencies the field was offered for: writing one for USD or for a pegged
     * coin would put a row in front of a number that is right by construction, and
     * `FxSnapshot` lets any stored row win over a peg.
     */
    private suspend fun saveHandRate(s: CurrencyEditUiState) {
        if (!s.handRateOffered) return
        exchangeRates.setHandRate(s.code, parseRate(s.handRate))
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
