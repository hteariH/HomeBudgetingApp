package com.personalmoney.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.FxSnapshot
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

data class CategorySlice(
    val categoryId: Long?,
    val name: String,
    val icon: String,
    val color: Long,
    val totalMinor: Long
)

data class DashboardUiState(
    val baseCurrency: String = "USD",
    val totalBalanceMinor: Long = 0L,
    val convertibleAccountsCount: Int = 0,
    val totalAccountsCount: Int = 0,
    val monthExpensesMinor: Long = 0L,
    val monthIncomeMinor: Long = 0L,
    val expenseByCategory: List<CategorySlice> = emptyList(),
    val recent: List<TransactionEntity> = emptyList(),
    val accountsById: Map<Long, AccountEntity> = emptyMap(),
    val categoriesById: Map<Long, CategoryEntity> = emptyMap()
) {
    val totalBalance: Money get() = Money(totalBalanceMinor, CurrencyCode(baseCurrency))
    val monthExpenses: Money get() = Money(monthExpensesMinor, CurrencyCode(baseCurrency))
    val monthIncome: Money get() = Money(monthIncomeMinor, CurrencyCode(baseCurrency))
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    exchangeRateRepository: ExchangeRateRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    // Emits every hour so month boundaries are picked up without a restart.
    private val hourlyTick = flow {
        while (true) {
            emit(Unit)
            delay(60 * 60 * 1_000L)
        }
    }

    private val fxSnapshotFlow = exchangeRateRepository.observeAll().map { FxSnapshot.from(it) }

    val uiState: StateFlow<DashboardUiState> = combine(
        userPreferences.baseCurrency,
        hourlyTick
    ) { base, _ -> base }.flatMapLatest { base ->
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val monthStart = LocalDate(today.year, today.month, 1).atStartOfDayIn(zone)
        val nextMonth = run {
            val m = today.month.value
            val y = today.year
            val nm = if (m == 12) LocalDate(y + 1, 1, 1) else LocalDate(y, m + 1, 1)
            nm.atStartOfDayIn(zone)
        }
        combine(
            accountRepository.observeActive(),
            accountRepository.observeBalances(),
            transactionRepository.observeRecent(10),
            categoryRepository.observeAll(),
            combine(
                transactionRepository.observeFiltered(from = monthStart, to = nextMonth),
                fxSnapshotFlow
            ) { tx, fx -> tx to fx }
        ) { accounts, balances, recent, categories, monthData ->
            val (monthTx, fx) = monthData
            val balancesById = balances.associateBy { it.accountId }
            val categoriesById = categories.associateBy { it.id }
            val accountsById = accounts.associateBy { it.id }

            var totalMinor = 0L
            var convertibleCount = 0
            for (account in accounts) {
                if (!account.includeInTotal) continue
                val row = balancesById[account.id] ?: continue
                val money = Money(row.balanceMinor, CurrencyCode(account.currencyCode))
                val converted = fx.convert(money, CurrencyCode(base))
                if (converted != null) {
                    totalMinor += converted.minor
                    convertibleCount++
                }
            }
            var monthExp = 0L
            var monthInc = 0L
            val expenseByCategoryAcc = HashMap<Long?, Long>()
            for (tx in monthTx) {
                val account = accountsById[tx.accountId] ?: continue
                val money = Money(tx.accountAmountMinor, CurrencyCode(account.currencyCode))
                val converted = fx.convert(money, CurrencyCode(base))?.minor ?: continue
                when (tx.type) {
                    TransactionType.EXPENSE -> {
                        monthExp += converted
                        expenseByCategoryAcc.merge(tx.categoryId, converted, Long::plus)
                    }
                    TransactionType.INCOME -> monthInc += converted
                    TransactionType.TRANSFER -> Unit
                }
            }
            val slices = expenseByCategoryAcc.entries
                .map { (catId, total) ->
                    val cat = catId?.let { categoriesById[it] }
                    CategorySlice(
                        categoryId = catId,
                        name = cat?.name ?: "Other",
                        icon = cat?.icon ?: "more_horiz",
                        color = cat?.color ?: 0xFF94A3B8L,
                        totalMinor = total
                    )
                }
                .sortedByDescending { it.totalMinor }
                .take(6)

            DashboardUiState(
                baseCurrency = base,
                totalBalanceMinor = totalMinor,
                convertibleAccountsCount = convertibleCount,
                totalAccountsCount = accounts.count { it.includeInTotal },
                monthExpensesMinor = monthExp,
                monthIncomeMinor = monthInc,
                expenseByCategory = slices,
                recent = recent,
                accountsById = accountsById,
                categoriesById = categoriesById
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
