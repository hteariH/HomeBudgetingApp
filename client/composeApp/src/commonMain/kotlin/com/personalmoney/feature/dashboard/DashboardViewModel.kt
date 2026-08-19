package com.personalmoney.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.FxSnapshot
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.ExchangeRateRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.shortMonthName
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class CategorySlice(
    val categoryId: Long?,
    val name: String,
    val icon: String,
    val color: Long,
    val totalMinor: Long
)

data class BudgetProgress(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val color: Long,
    val spentMinor: Long,
    val limitMinor: Long
) {
    /** 0f..1f for the bar; spend can exceed the limit (handled by [isOver]). */
    val fraction: Float get() = if (limitMinor <= 0L) 0f
        else (spentMinor.toDouble() / limitMinor.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val isOver: Boolean get() = spentMinor > limitMinor
}

data class DashboardUiState(
    val baseCurrency: String = "USD",
    val totalBalanceMinor: Long = 0L,
    val convertibleAccountsCount: Int = 0,
    val totalAccountsCount: Int = 0,
    val monthExpensesMinor: Long = 0L,
    val monthIncomeMinor: Long = 0L,
    val expenseByCategory: List<CategorySlice> = emptyList(),
    val moreExpenseCategories: Int = 0,
    val budgets: List<BudgetProgress> = emptyList(),
    val netWorthTrend: List<Long> = emptyList(),
    /** Income and spending per month, oldest first - six points, like the trend. */
    val monthlyFlow: List<MonthFlow> = emptyList(),
    /** Every account with its own balance, for the summary card. */
    val accounts: List<Account> = emptyList(),
    val recent: List<Transaction> = emptyList(),
    val accountsById: Map<Long, Account> = emptyMap(),
    val categoriesById: Map<Long, Category> = emptyMap()
) {
    val totalBalance: Money get() = Money(totalBalanceMinor, CurrencyCode(baseCurrency))
    val monthExpenses: Money get() = Money(monthExpensesMinor, CurrencyCode(baseCurrency))
    val monthIncome: Money get() = Money(monthIncomeMinor, CurrencyCode(baseCurrency))

    /** What the month actually added or took away. Negative is spending more than earning. */
    val monthNet: Money get() = Money(monthIncomeMinor - monthExpensesMinor, CurrencyCode(baseCurrency))
}

/** One month's two totals, in base-currency minor units. */
data class MonthFlow(val label: String, val incomeMinor: Long, val expenseMinor: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel constructor(
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
            val m = today.monthNumber
            val y = today.year
            val nm = if (m == 12) LocalDate(y + 1, 1, 1) else LocalDate(y, m + 1, 1)
            nm.atStartOfDayIn(zone)
        }
        // No separate balance query: the shared Account already carries its summed balance.
        combine(
            accountRepository.observeActive(),
            transactionRepository.observeRecent(10),
            categoryRepository.observeAll(),
            combine(
                transactionRepository.observeFiltered(from = monthStart, to = nextMonth),
                fxSnapshotFlow
            ) { tx, fx -> tx to fx }
        ) { accounts, recent, categories, monthData ->
            val (monthTx, fx) = monthData
            val categoriesById = categories.associateBy { it.id }
            val accountsById = accounts.associateBy { it.id }

            var totalMinor = 0L
            var convertibleCount = 0
            for (account in accounts) {
                if (!account.includeInTotal) continue
                val money = Money(account.balanceMinor, CurrencyCode(account.currencyCode))
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
                        // Not Map.merge: that is a JVM method, and this map is a plain
                        // MutableMap in shared code.
                        expenseByCategoryAcc[tx.categoryId] =
                            (expenseByCategoryAcc[tx.categoryId] ?: 0L) + converted
                    }
                    TransactionType.INCOME -> monthInc += converted
                    TransactionType.TRANSFER -> Unit
                }
            }
            val allSlices = expenseByCategoryAcc.entries
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
            val slices = allSlices.take(6)
            val moreExpenseCategories = (allSlices.size - slices.size).coerceAtLeast(0)

            val budgets = categories
                .filter { !it.archived && it.type == com.personalmoney.core.domain.CategoryType.EXPENSE && it.budgetLimitMinor != null }
                .map { cat ->
                    BudgetProgress(
                        categoryId = cat.id,
                        name = cat.name,
                        icon = cat.icon,
                        color = cat.color,
                        spentMinor = expenseByCategoryAcc[cat.id] ?: 0L,
                        limitMinor = cat.budgetLimitMinor!!
                    )
                }
                .sortedByDescending { it.fraction }

            // Net worth (base currency) at the end of each of the last 6 months — reconstructed
            // from transaction history so no snapshot storage is needed.
            val currentMonthStart = LocalDate(today.year, today.month, 1)
            val netWorthTrend = (5 downTo 0).map { i ->
                val mStart = currentMonthStart.minus(i, DateTimeUnit.MONTH)
                val mEndExclusive = mStart.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(zone)
                val asOf = minOf(mEndExclusive, Clock.System.now())
                accountRepository.balancesAsOf(asOf).sumOf { row ->
                    fx.convert(Money(row.balanceMinor, CurrencyCode(row.currencyCode)), CurrencyCode(base))?.minor ?: 0L
                }
            }

            // Income and spending for the same six months the trend covers. One pass over
            // the ledger the screen already has, bucketed by month, rather than six more
            // queries - the rows are in memory either way.
            val flowByMonth = (5 downTo 0).map { i ->
                val mStart = currentMonthStart.minus(i, DateTimeUnit.MONTH)
                val from = mStart.atStartOfDayIn(zone)
                val toExclusive = mStart.plus(1, DateTimeUnit.MONTH).atStartOfDayIn(zone)
                var income = 0L
                var expense = 0L
                for (tx in recent + monthTx) {
                    if (tx.occurredAt < from || tx.occurredAt >= toExclusive) continue
                    val converted = fx.convert(
                        Money(tx.amountMinor, CurrencyCode(tx.currencyCode)),
                        CurrencyCode(base),
                    )?.minor ?: continue
                    when (tx.type) {
                        TransactionType.INCOME -> income += converted
                        TransactionType.EXPENSE -> expense += converted
                        else -> Unit
                    }
                }
                MonthFlow(shortMonthName(mStart.monthNumber), income, expense)
            }

            DashboardUiState(
                baseCurrency = base,
                monthlyFlow = flowByMonth,
                accounts = accounts,
                totalBalanceMinor = totalMinor,
                convertibleAccountsCount = convertibleCount,
                totalAccountsCount = accounts.count { it.includeInTotal },
                monthExpensesMinor = monthExp,
                monthIncomeMinor = monthInc,
                expenseByCategory = slices,
                moreExpenseCategories = moreExpenseCategories,
                budgets = budgets,
                netWorthTrend = netWorthTrend,
                recent = recent,
                accountsById = accountsById,
                categoriesById = categoriesById
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
