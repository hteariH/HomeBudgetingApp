package com.personalmoney.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.data.LedgerFilterRequest
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import com.personalmoney.core.domain.toMajorUnits
import com.personalmoney.core.domain.toDecimalString

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
    val transactions: List<Transaction> = emptyList(),
    val accountsById: Map<Long, Account> = emptyMap(),
    val categoriesById: Map<Long, Category> = emptyMap(),
    /** For a collapsed transfer row, its partner leg (the other side) when both are in the result. */
    val transferCounterpartsById: Map<Long, Transaction> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val ledgerFilter: LedgerFilterRequest,
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
                    (t.transferCounterpartAccountId?.let { accountsById[it]?.name }.orEmpty()).lowercase().contains(q) ||
                    t.currencyCode.lowercase().contains(q) ||
                    amountMatches(t, q)
            }
            val collapsed = collapseTransfers(filtered)
            val transferLegsByGroup = filtered
                .filter { it.transferGroupId != null }
                .groupBy { it.transferGroupId!! }
            val counterpartsById = buildMap {
                for (t in collapsed) {
                    val g = t.transferGroupId ?: continue
                    transferLegsByGroup[g]?.firstOrNull { it.id != t.id }?.let { put(t.id, it) }
                }
            }
            TransactionsUiState(
                filter = filter,
                transactions = collapsed,
                accountsById = accountsById,
                categoriesById = categoriesById,
                transferCounterpartsById = counterpartsById
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    /**
     * A transfer is stored as two rows (a negative debit on the source account and a positive credit
     * on the destination). Show it once: drop the credit leg whenever its debit partner is also in the
     * list, so each transfer renders as a single "from -> to" row. If only one leg survives the account
     * filter (e.g. filtering by the destination account), that leg is kept.
     */
    private fun collapseTransfers(list: List<Transaction>): List<Transaction> {
        val groupsWithDebit = list.asSequence()
            .filter { it.transferGroupId != null && it.amountMinor < 0 }
            .mapNotNull { it.transferGroupId }
            .toHashSet()
        return list.filterNot { t ->
            t.transferGroupId != null && t.amountMinor > 0 && t.transferGroupId in groupsWithDebit
        }
    }

    /** Matches the query against the transaction's amount written in major units (e.g. "15" or "12.50"). */
    private fun amountMatches(t: Transaction, q: String): Boolean {
        if (q.none { it.isDigit() }) return false
        val decimals = CurrencyRegistry.decimalsOrFallback(t.currencyCode)
        val plain = t.accountAmountMinor.toDecimalString(decimals).removePrefix("-")
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

    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            if (transaction.transferGroupId != null) {
                transactionRepository.deleteTransfer(transaction.transferGroupId)
            } else {
                transactionRepository.delete(transaction.id)
            }
        }
    }

    /** Quick-repeat: insert a copy of a transaction dated now. Transfers are not duplicated. */
    fun duplicate(transaction: Transaction) {
        if (transaction.transferGroupId != null) return
        viewModelScope.launch {
            val now = Clock.System.now()
            transactionRepository.insert(
                // Blank uuid as well as a zero id: a duplicate is a new row, and reusing the
                // original's uuid would make the server treat it as an edit of that one.
                transaction.copy(id = 0L, uuid = "", occurredAt = now, subscriptionId = null)
            )
        }
    }
    init {
        // Applied as a starting filter, not a lock: the account dropdown still shows it and
        // the clear-filters action still removes it. Consumed straight away so returning to
        // this tab later does not silently re-narrow a list the user has since widened.
        viewModelScope.launch {
            ledgerFilter.accountId.collect { requested ->
                if (requested != null) {
                    setAccount(requested)
                    ledgerFilter.consume()
                }
            }
        }
    }

}
