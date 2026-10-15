package com.personalmoney.feature.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.network.serverMessage
import org.jetbrains.compose.resources.getString
import com.personalmoney.resources.*
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Currency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.personalmoney.core.domain.roundTo
import com.personalmoney.core.domain.toDecimalString
import com.personalmoney.core.domain.toMinorUnits

data class AccountEditUiState(
    val id: Long? = null,
    val name: String = "",
    val currencyCode: String = "USD",
    val initialBalance: String = "0",
    val icon: String = "wallet",
    val color: Long = 0xFF3D4499L,
    val includeInTotal: Boolean = true,
    val archived: Boolean = false,
    val note: String = "",
    val availableCurrencies: List<Currency> = emptyList(),
    val transactionCount: Int = 0,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
) {
    val isEditing: Boolean get() = id != null
    val canSave: Boolean get() = name.isNotBlank() && currencyCode.isNotBlank() && !saving
}

class AccountEditViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val accountId: Long? = savedStateHandle.get<String>("accountId")?.toLongOrNull()
    private val internalState = MutableStateFlow(AccountEditUiState(id = accountId))

    val uiState: StateFlow<AccountEditUiState> = combine(
        internalState,
        currencyRepository.observeActive(),
        currencyRepository.observeAll()
    ) { local, active, all ->
        // Active list is what the user picks from. When editing an account whose currency
        // has been deactivated, still keep it in the dropdown so the existing selection
        // doesn't silently swap to something else.
        val current = all.firstOrNull { it.code == local.currencyCode }
        val list = if (current != null && active.none { it.code == current.code }) {
            active + current
        } else {
            active
        }
        local.copy(availableCurrencies = list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountEditUiState())

    init {
        viewModelScope.launch {
            if (accountId != null) {
                accountRepository.findById(accountId)?.let { acc ->
                    val decimals = currencyDecimals(acc.currencyCode)
                    val txCount = accountRepository.transactionCount(acc.id)
                    internalState.value = AccountEditUiState(
                        id = acc.id,
                        name = acc.name,
                        currencyCode = acc.currencyCode,
                        initialBalance = acc.initialBalanceMinor.toDecimalString(decimals),
                        icon = acc.icon,
                        color = acc.color,
                        includeInTotal = acc.includeInTotal,
                        archived = acc.archived,
                        note = acc.note ?: "",
                        transactionCount = txCount
                    )
                }
            } else {
                val base = userPreferences.baseCurrency.first()
                internalState.update { it.copy(currencyCode = base) }
            }
        }
    }

    fun setName(value: String) = internalState.update { it.copy(name = value) }
    fun setCurrency(code: String) = internalState.update { it.copy(currencyCode = code) }
    fun setInitialBalance(value: String) =
        internalState.update { it.copy(initialBalance = value.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }) }
    fun setIcon(icon: String) = internalState.update { it.copy(icon = icon) }
    fun setColor(color: Long) = internalState.update { it.copy(color = color) }
    fun setIncludeInTotal(value: Boolean) = internalState.update { it.copy(includeInTotal = value) }
    fun setArchived(value: Boolean) = internalState.update { it.copy(archived = value) }
    fun setNote(value: String) = internalState.update { it.copy(note = value) }
    fun clearError() = internalState.update { it.copy(error = null) }

    fun save() {
        val st = internalState.value
        if (!st.canSave) return
        internalState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val outcome = runCatching {
                val decimals = currencyDecimals(st.currencyCode)
                val balanceMinor = BigDecimal.parseString(st.initialBalance.ifBlank { "0" })
                    .toMinorUnits(decimals)

                if (st.id == null) {
                    val newId = accountRepository.create(
                        Account(
                            name = st.name.trim(),
                            currencyCode = st.currencyCode,
                            initialBalanceMinor = balanceMinor,
                            icon = st.icon,
                            color = st.color,
                            includeInTotal = st.includeInTotal,
                            archived = st.archived,
                            note = st.note.takeIf { it.isNotBlank() }
                        )
                    )
                    // Auto-activate currency
                    currencyRepository.setActive(st.currencyCode, true)
                    // If first account ever, set default
                    if (userPreferences.defaultAccountId.first() == null) {
                        userPreferences.setDefaultAccount(newId)
                    }
                } else {
                    val existing = accountRepository.findById(st.id) ?: return@runCatching
                    accountRepository.update(
                        existing.copy(
                            name = st.name.trim(),
                            currencyCode = st.currencyCode,
                            initialBalanceMinor = balanceMinor,
                            icon = st.icon,
                            color = st.color,
                            includeInTotal = st.includeInTotal,
                            archived = st.archived,
                            note = st.note.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            // Not `onFailure`: reading the server's sentence is a suspend call, and the
            // sentence is the whole point here - a bare status line from Ktor tells a
            // user nothing, while the server's own message says what was wrong.
            val failure = outcome.exceptionOrNull()
            if (failure == null) {
                internalState.update { it.copy(saving = false, saved = true) }
            } else {
                val message = failure.serverMessage() ?: getString(Res.string.common_request_failed)
                internalState.update { it.copy(saving = false, error = message) }
            }
        }
    }

    fun delete() {
        val id = internalState.value.id ?: return
        viewModelScope.launch {
            runCatching {
                accountRepository.delete(id)
                // Clear the stored default if it pointed at the deleted account.
                if (userPreferences.defaultAccountId.first() == id) {
                    userPreferences.setDefaultAccount(null)
                }
            }
                .onSuccess { internalState.update { it.copy(saved = true) } }
                .also { result ->
                    result.exceptionOrNull()?.let { e ->
                        val message = e.serverMessage() ?: getString(Res.string.common_request_failed)
                        internalState.update { it.copy(error = message) }
                    }
                }
        }
    }

    private fun currencyDecimals(code: String): Int =
        CurrencyRegistry.decimalsOrFallback(code)
}
