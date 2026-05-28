package com.personalmoney.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.database.dao.AccountBalanceRow
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountWithBalance(
    val account: AccountEntity,
    val balance: Money
)

data class AccountsUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
    val baseCurrency: String = "USD"
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.observeAll(),
        accountRepository.observeBalances(),
        userPreferences.baseCurrency
    ) { all, balances, base ->
        val balanceMap: Map<Long, AccountBalanceRow> = balances.associateBy { it.accountId }
        val withBalance = all.map { account ->
            val row = balanceMap[account.id]
            AccountWithBalance(
                account = account,
                balance = Money(
                    minor = row?.balanceMinor ?: account.initialBalanceMinor,
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
