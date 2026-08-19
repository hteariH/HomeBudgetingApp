package com.personalmoney.feature.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.domain.model.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CurrenciesUiState(
    val query: String = "",
    val baseCurrency: String = "USD",
    val active: List<Currency> = emptyList(),
    val available: List<Currency> = emptyList(),
    val errorMessage: String? = null
)

class CurrenciesViewModel constructor(
    private val repository: CurrencyRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val errorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CurrenciesUiState> = combine(
        repository.observeAll(),
        queryFlow,
        preferences.baseCurrency,
        errorFlow
    ) { all, query, base, error ->
        val needle = query.trim().lowercase()
        val matches = if (needle.isEmpty()) all
        else all.filter {
            it.code.lowercase().contains(needle) || it.name.lowercase().contains(needle)
        }
        CurrenciesUiState(
            query = query,
            baseCurrency = base,
            active = matches.filter { it.active },
            available = matches.filter { !it.active },
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrenciesUiState())

    fun setQuery(value: String) { queryFlow.value = value }
    fun clearError() { errorFlow.value = null }

    fun toggle(currency: Currency) {
        viewModelScope.launch {
            val base = preferences.baseCurrency.first()
            if (currency.active && currency.code == base) {
                errorFlow.value = "Pick a different base currency before disabling this one"
                return@launch
            }
            repository.setActive(currency.code, !currency.active)
        }
    }

    fun delete(currency: Currency, onUsageBlocked: (Int) -> Unit) {
        viewModelScope.launch {
            val uses = repository.usageCount(currency.code)
            if (uses > 0) {
                onUsageBlocked(uses)
                return@launch
            }
            repository.delete(currency.code)
        }
    }
}
