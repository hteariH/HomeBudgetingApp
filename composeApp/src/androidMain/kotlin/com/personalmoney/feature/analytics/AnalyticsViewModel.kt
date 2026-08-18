package com.personalmoney.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalmoney.core.currency.FxSnapshot
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

data class MonthlyBar(
    val label: String,
    val expenses: Money,
    val income: Money,
    val monthIndex: Int
)

data class AnalyticsUiState(
    val baseCurrency: String = "USD",
    val months: List<MonthlyBar> = emptyList(),
    val totalExpenses: Money = Money(0L, CurrencyCode("USD")),
    val totalIncome: Money = Money(0L, CurrencyCode("USD"))
) {
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
private fun monthLabel(monthNumber: Int): String =
    java.time.Month.of(monthNumber)
        .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel constructor(
    private val transactionRepository: TransactionRepository,
    exchangeRateRepository: ExchangeRateRepository,
    userPreferences: UserPreferences
) : ViewModel() {

    private val monthCount = 6
    private val fxFlow = exchangeRateRepository.observeAll().map { FxSnapshot.from(it) }

    val uiState: StateFlow<AnalyticsUiState> = userPreferences.baseCurrency.flatMapLatest { base ->
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val currentMonth = now.toLocalDateTime(zone).date.let { LocalDate(it.year, it.month, 1) }

        // Build month ranges for last N months
        val months = (monthCount - 1 downTo 0).map { i ->
            val start = currentMonth.minus(i, DateTimeUnit.MONTH)
            val end = start.plus(1, DateTimeUnit.MONTH)
            Triple(start, start.atStartOfDayIn(zone), end.atStartOfDayIn(zone))
        }

        // Build a combined flow of all monthly totals
        val monthFlows = months.map { (monthDate, fromInstant, toInstant) ->
            combine(
                transactionRepository.observeFiltered(from = fromInstant, to = toInstant),
                fxFlow
            ) { txList, fx ->
                var expMinor = 0L
                var incMinor = 0L
                for (tx in txList) {
                    val money = Money(tx.accountAmountMinor, CurrencyCode(tx.currencyCode))
                    val converted = fx.convert(money, CurrencyCode(base))?.minor ?: continue
                    when (tx.type) {
                        TransactionType.EXPENSE -> expMinor += converted
                        TransactionType.INCOME -> incMinor += converted
                        TransactionType.TRANSFER -> Unit
                    }
                }
                MonthlyBar(
                    label = monthLabel(monthDate.monthNumber),
                    expenses = Money(expMinor, CurrencyCode(base)),
                    income = Money(incMinor, CurrencyCode(base)),
                    monthIndex = monthDate.monthNumber - 1
                )
            }
        }

        @Suppress("UNCHECKED_CAST")
        combine(*monthFlows.toTypedArray()) { bars ->
            val barList = (bars as Array<MonthlyBar>).toList()
            val totalExp = barList.sumOf { it.expenses.minor }
            val totalInc = barList.sumOf { it.income.minor }
            AnalyticsUiState(
                baseCurrency = base,
                months = barList,
                totalExpenses = Money(totalExp, CurrencyCode(base)),
                totalIncome = Money(totalInc, CurrencyCode(base))
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())
}
