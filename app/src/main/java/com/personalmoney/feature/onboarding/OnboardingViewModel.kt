package com.personalmoney.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CurrencyEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val totalSteps: Int = 3,
    val currencies: List<CurrencyEntity> = emptyList(),
    val selectedCurrencyCode: String = "USD",
    val accountName: String = "",
    val saving: Boolean = false,
    val done: Boolean = false,
    val showAllCurrenciesDialog: Boolean = false,
    val allCurrencies: List<CurrencyEntity> = emptyList(),
    val currencySearchQuery: String = ""
) {
    val canProceed: Boolean get() = when (step) {
        1 -> selectedCurrencyCode.isNotBlank()
        2 -> accountName.isNotBlank()
        else -> true
    }

    val filteredAllCurrencies: List<CurrencyEntity> get() {
        if (currencySearchQuery.isBlank()) return allCurrencies
        val q = currencySearchQuery.trim().lowercase()
        return allCurrencies.filter {
            it.code.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
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
            currencies = secondary[0] as List<CurrencyEntity>,
            allCurrencies = secondary[1] as List<CurrencyEntity>,
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
                        AccountEntity(
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
