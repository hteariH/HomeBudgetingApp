package com.personalmoney.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountWithBalance(
    val account: Account,
    val balance: Money
)

data class AccountsUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
    val baseCurrency: String = "USD"
)

class AccountsViewModel constructor(
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    // No separate balance query: the shared Account already carries the summed balance,
    // because both stores have to work it out anyway - Room from the ledger, the server
    // before it answers.
    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.observeAll(),
        userPreferences.baseCurrency
    ) { all, base ->
        val withBalance = all.map { account ->
            AccountWithBalance(
                account = account,
                balance = Money(
                    minor = account.balanceMinor,
                    currency = CurrencyCode(account.currencyCode)
                )
            )
        }
        AccountsUiState(
            accounts = withBalance.filter { !it.account.archived },
            archived = withBalance.filter { it.account.archived },
            baseCurrency = base
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { accountRepository.setArchived(id, archived) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { accountRepository.delete(id) }
    }

    fun reorder(ids: List<Long>) {
        viewModelScope.launch { accountRepository.reorder(ids) }
    }
}
