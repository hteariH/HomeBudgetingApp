package com.personalmoney.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.FxSnapshot
import com.personalmoney.core.domain.shortMonthName
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.ExchangeRateRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
import kotlin.math.roundToInt

enum class AnalyticsPeriod(val monthsCount: Int) {
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    TWELVE_MONTHS(12)
}

data class MonthCategorySlice(
    val categoryId: Long?,
    val name: String,
    val icon: String,
    val color: Long,
    val amount: Money,
    val percentage: Int
)

data class MonthlyBar(
    val label: String,
    val expenses: Money,
    val income: Money,
    val monthIndex: Int,
    val fullLabel: String = label,
    val year: Int = 0,
    val monthNumber: Int = 0,
    val expenseCategories: List<MonthCategorySlice> = emptyList(),
    val incomeCategories: List<MonthCategorySlice> = emptyList()
)

data class AnalyticsUiState(
    val baseCurrency: String = "USD",
    val period: AnalyticsPeriod = AnalyticsPeriod.SIX_MONTHS,
    val months: List<MonthlyBar> = emptyList(),
    val totalExpenses: Money = Money(0L, CurrencyCode("USD")),
    val totalIncome: Money = Money(0L, CurrencyCode("USD")),
    val selectedMonthIndex: Int? = null,
    val selectedCategoryType: TransactionType = TransactionType.EXPENSE
) {
    val selectedMonth: MonthlyBar? get() =
        months.firstOrNull { it.monthIndex == selectedMonthIndex } ?: months.lastOrNull()

    // Always at least 1 so charts never divide by zero (empty months or all-zero bars).
    val maxValue: Long get() =
        (months.maxOfOrNull { maxOf(it.expenses.minor, it.income.minor) } ?: 0L).coerceAtLeast(1L)

    // months are ordered oldest -> newest, so the last is the current month.
    val currentMonth: MonthlyBar? get() = months.lastOrNull()
    private val previousMonth: MonthlyBar? get() = months.getOrNull(months.size - 2)

    /** Percent change in expenses vs the previous month, or null when not computable. */
    val expenseChangePercent: Int? get() {
        val prev = previousMonth?.expenses?.minor ?: return null
        val curr = currentMonth?.expenses?.minor ?: return null
        if (prev <= 0L) return null
        return (((curr - prev).toDouble() / prev) * 100).roundToInt()
    }

    /** Savings rate for the current month: (income - expenses) / income, as a percent. */
    val savingsRatePercent: Int? get() {
        val income = currentMonth?.income?.minor ?: return null
        val expenses = currentMonth?.expenses?.minor ?: return null
        if (income <= 0L) return null
        return (((income - expenses).toDouble() / income) * 100).roundToInt()
    }
}

/** Localized short month name (Jan / янв. / січ.) following the active app locale. */
private fun monthLabel(monthNumber: Int): String = shortMonthName(monthNumber)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    exchangeRateRepository: ExchangeRateRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    private val periodFlow = MutableStateFlow(AnalyticsPeriod.SIX_MONTHS)
    private val selectedMonthIndexFlow = MutableStateFlow<Int?>(null)
    private val selectedCategoryTypeFlow = MutableStateFlow(TransactionType.EXPENSE)
    private val fxFlow = exchangeRateRepository.observeAll().map { FxSnapshot.from(it) }

    fun setPeriod(period: AnalyticsPeriod) {
        periodFlow.value = period
    }

    fun selectMonth(monthIndex: Int?) {
        selectedMonthIndexFlow.value = monthIndex
    }

    fun setCategoryType(type: TransactionType) {
        selectedCategoryTypeFlow.value = type
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        userPreferences.baseCurrency,
        periodFlow,
        categoryRepository.observeAll()
    ) { base, period, categories ->
        Triple(base, period, categories)
    }.flatMapLatest { (base, period, categories) ->
        val categoriesById = categories.associateBy { it.id }
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val currentMonth = now.toLocalDateTime(zone).date.let { LocalDate(it.year, it.month, 1) }

        // Build month ranges for last N months
        val months = (period.monthsCount - 1 downTo 0).map { i ->
            val start = currentMonth.minus(i, DateTimeUnit.MONTH)
            val end = start.plus(1, DateTimeUnit.MONTH)
            Triple(start, start.atStartOfDayIn(zone), end.atStartOfDayIn(zone))
        }

        // Build a combined flow of all monthly totals + category breakdown
        val monthFlows = months.map { (monthDate, fromInstant, toInstant) ->
            combine(
                transactionRepository.observeFiltered(from = fromInstant, to = toInstant),
                fxFlow
            ) { txList, fx ->
                var expMinor = 0L
                var incMinor = 0L
                val expByCat = mutableMapOf<Long?, Long>()
                val incByCat = mutableMapOf<Long?, Long>()

                for (tx in txList) {
                    val money = Money(tx.accountAmountMinor, CurrencyCode(tx.currencyCode))
                    val converted = fx.convert(money, CurrencyCode(base))?.minor ?: continue
                    when (tx.type) {
                        TransactionType.EXPENSE -> {
                            expMinor += converted
                            expByCat[tx.categoryId] = (expByCat[tx.categoryId] ?: 0L) + converted
                        }
                        TransactionType.INCOME -> {
                            incMinor += converted
                            incByCat[tx.categoryId] = (incByCat[tx.categoryId] ?: 0L) + converted
                        }
                        TransactionType.TRANSFER -> Unit
                    }
                }

                fun toSlices(map: Map<Long?, Long>, total: Long): List<MonthCategorySlice> {
                    if (total <= 0L) return emptyList()
                    return map.entries
                        .map { (catId, amount) ->
                            val cat = catId?.let { categoriesById[it] }
                            val pct = ((amount.toDouble() / total) * 100).roundToInt()
                            MonthCategorySlice(
                                categoryId = catId,
                                name = cat?.name ?: "Other",
                                icon = cat?.icon ?: "more_horiz",
                                color = cat?.color ?: 0xFF94A3B8L,
                                amount = Money(amount, CurrencyCode(base)),
                                percentage = pct
                            )
                        }
                        .sortedByDescending { it.amount.minor }
                }

                val year = monthDate.year
                val monthNum = monthDate.monthNumber
                val label = monthLabel(monthNum)
                val fullLabel = "$label $year"

                MonthlyBar(
                    label = label,
                    fullLabel = fullLabel,
                    expenses = Money(expMinor, CurrencyCode(base)),
                    income = Money(incMinor, CurrencyCode(base)),
                    monthIndex = monthNum - 1,
                    year = year,
                    monthNumber = monthNum,
                    expenseCategories = toSlices(expByCat, expMinor),
                    incomeCategories = toSlices(incByCat, incMinor)
                )
            }
        }

        @Suppress("UNCHECKED_CAST")
        combine(
            combine(*monthFlows.toTypedArray()) { bars -> (bars as Array<MonthlyBar>).toList() },
            selectedMonthIndexFlow,
            selectedCategoryTypeFlow
        ) { barList, selectedIdx, catType ->
            val totalExp = barList.sumOf { it.expenses.minor }
            val totalInc = barList.sumOf { it.income.minor }
            AnalyticsUiState(
                baseCurrency = base,
                period = period,
                months = barList,
                totalExpenses = Money(totalExp, CurrencyCode(base)),
                totalIncome = Money(totalInc, CurrencyCode(base)),
                selectedMonthIndex = selectedIdx,
                selectedCategoryType = catType
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())
}
