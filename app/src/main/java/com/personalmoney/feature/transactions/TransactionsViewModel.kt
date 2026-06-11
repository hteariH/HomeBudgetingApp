package com.personalmoney.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import javax.inject.Inject

data class TransactionsFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val type: TransactionType? = null,
    val query: String = ""
)

data class TransactionsUiState(
    val filter: TransactionsFilter = TransactionsFilter(),
    val transactions: List<TransactionEntity> = emptyList(),
    val accountsById: Map<Long, AccountEntity> = emptyMap(),
    val categoriesById: Map<Long, CategoryEntity> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(TransactionsFilter())

    val uiState: StateFlow<TransactionsUiState> = filterFlow.flatMapLatest { filter ->
        val zone = TimeZone.currentSystemDefault()
        val from = filter.from?.atStartOfDayIn(zone)
        val to = filter.to?.atStartOfDayIn(zone)?.plus(1, DateTimeUnit.DAY, zone)
        combine(
            transactionRepository.observeFiltered(
                from = from,
                to = to,
                accountId = filter.accountId,
                categoryId = filter.categoryId,
                type = filter.type
            ),
            accountRepository.observeAll(),
            categoryRepository.observeAll()
        ) { tx, accounts, categories ->
            val accountsById = accounts.associateBy { it.id }
            val categoriesById = categories.associateBy { it.id }
            val q = filter.query.trim().lowercase()
            val filtered = if (q.isEmpty()) tx else tx.filter { t ->
                t.note?.lowercase()?.contains(q) == true ||
                    (t.categoryId?.let { categoriesById[it]?.name }.orEmpty()).lowercase().contains(q) ||
                    (accountsById[t.accountId]?.name.orEmpty()).lowercase().contains(q) ||
                    t.currencyCode.lowercase().contains(q) ||
                    amountMatches(t, q)
            }
            TransactionsUiState(
                filter = filter,
                transactions = filtered,
                accountsById = accountsById,
                categoriesById = categoriesById
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    /** Matches the query against the transaction's amount written in major units (e.g. "15" or "12.50"). */
    private fun amountMatches(t: TransactionEntity, q: String): Boolean {
        if (q.none { it.isDigit() }) return false
        val decimals = CurrencyRegistry.decimalsOrFallback(t.currencyCode)
        val plain = java.math.BigDecimal(t.accountAmountMinor).movePointLeft(decimals).abs().toPlainString()
        return plain.contains(q)
    }

    fun setQuery(value: String) { filterFlow.value = filterFlow.value.copy(query = value) }
    fun setType(type: TransactionType?) { filterFlow.value = filterFlow.value.copy(type = type) }
    fun setAccount(id: Long?) { filterFlow.value = filterFlow.value.copy(accountId = id) }
    fun setCategory(id: Long?) { filterFlow.value = filterFlow.value.copy(categoryId = id) }
    fun setDateRange(from: LocalDate?, to: LocalDate?) {
        filterFlow.value = filterFlow.value.copy(from = from, to = to)
    }
    fun clearFilters() { filterFlow.value = TransactionsFilter() }

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch {
            if (transaction.transferGroupId != null) {
                transactionRepository.deleteTransfer(transaction.transferGroupId)
            } else {
                transactionRepository.delete(transaction.id)
            }
        }
    }
}
