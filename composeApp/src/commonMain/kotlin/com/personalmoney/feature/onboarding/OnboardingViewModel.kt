package com.personalmoney.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

data class OnboardingUiState(
    val step: Int = 0,
    val totalSteps: Int = 3,
    val currencies: List<Currency> = emptyList(),
    val selectedCurrencyCode: String = "USD",
    val accountName: String = "",
    val saving: Boolean = false,
    val done: Boolean = false,
    val showAllCurrenciesDialog: Boolean = false,
    val allCurrencies: List<Currency> = emptyList(),
    val currencySearchQuery: String = ""
) {
    val canProceed: Boolean get() = when (step) {
        1 -> selectedCurrencyCode.isNotBlank()
        2 -> accountName.isNotBlank()
        else -> true
    }

    val filteredAllCurrencies: List<Currency> get() {
        if (currencySearchQuery.isBlank()) return allCurrencies
        val q = currencySearchQuery.trim().lowercase()
        return allCurrencies.filter {
            it.code.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
    }
}

class OnboardingViewModel constructor(
    private val userPreferences: UserPreferences,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository
) : ViewModel() {

    private val stepFlow = MutableStateFlow(0)
    private val selectedCurrencyFlow = MutableStateFlow("USD")
    private val accountNameFlow = MutableStateFlow("")
    private val savingFlow = MutableStateFlow(false)
    private val doneFlow = MutableStateFlow(false)
    private val showAllCurrenciesDialogFlow = MutableStateFlow(false)
    private val currencySearchQueryFlow = MutableStateFlow("")

    val uiState: StateFlow<OnboardingUiState> = combine(
        combine(stepFlow, selectedCurrencyFlow, accountNameFlow, savingFlow, doneFlow) { step, currency, name, saving, done ->
            listOf<Any>(step, currency, name, saving, done)
        },
        combine(
            currencyRepository.observeActive(),
            currencyRepository.observeAll(),
            showAllCurrenciesDialogFlow,
            currencySearchQueryFlow
        ) { active, all, showDialog, query ->
            listOf<Any>(active, all, showDialog, query)
        }
    ) { primary, secondary ->
        @Suppress("UNCHECKED_CAST")
        OnboardingUiState(
            step = primary[0] as Int,
            selectedCurrencyCode = primary[1] as String,
            accountName = primary[2] as String,
            saving = primary[3] as Boolean,
            done = primary[4] as Boolean,
            currencies = secondary[0] as List<Currency>,
            allCurrencies = secondary[1] as List<Currency>,
            showAllCurrenciesDialog = secondary[2] as Boolean,
            currencySearchQuery = secondary[3] as String
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun selectCurrency(code: String) { selectedCurrencyFlow.value = code }
    fun setAccountName(name: String) { accountNameFlow.value = name }
    fun showAllCurrenciesDialog() { showAllCurrenciesDialogFlow.value = true }
    fun dismissAllCurrenciesDialog() {
        showAllCurrenciesDialogFlow.value = false
        currencySearchQueryFlow.value = ""
    }
    fun setCurrencySearchQuery(query: String) { currencySearchQueryFlow.value = query }
    fun selectCurrencyFromAll(code: String) {
        selectedCurrencyFlow.value = code
        viewModelScope.launch { currencyRepository.setActive(code, true) }
        dismissAllCurrenciesDialog()
    }

    fun next() {
        val state = uiState.value
        if (state.step < state.totalSteps - 1) {
            if (state.step == 1) {
                viewModelScope.launch {
                    userPreferences.setBaseCurrency(state.selectedCurrencyCode)
                }
            }
            stepFlow.value++
        }
    }

    fun skip() {
        finish()
    }

    fun finish() {
        val state = uiState.value
        viewModelScope.launch {
            savingFlow.value = true
            runCatching {
                userPreferences.setBaseCurrency(state.selectedCurrencyCode)
                if (state.accountName.isNotBlank()) {
                    accountRepository.create(
                        Account(
                            name = state.accountName,
                            currencyCode = state.selectedCurrencyCode,
                            initialBalanceMinor = 0L,
                            icon = "account_balance_wallet",
                            color = 0xFF6750A4L,
                            archived = false,
                            sortOrder = 0,
                            includeInTotal = true
                        )
                    )
                }
                userPreferences.setOnboarded(true)
            }.onSuccess {
                doneFlow.value = true
            }
            savingFlow.value = false
        }
    }
}
