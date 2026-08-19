package com.personalmoney.feature.analytics

import app.cash.turbine.test
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.ExchangeRateRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnalyticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Clock.System.now()

    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val exchangeRateRepository = mockk<ExchangeRateRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)

    private fun setupEmptyMocks(baseCurrency: String = "USD") {
        every { userPreferences.baseCurrency } returns flowOf(baseCurrency)
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
    }

    private fun createViewModel(): AnalyticsViewModel = AnalyticsViewModel(
        transactionRepository = transactionRepository,
        exchangeRateRepository = exchangeRateRepository,
        userPreferences = userPreferences
    )

    // ── Month structure ──────────────────────────────────────────────────────

    @Test
    fun `produces exactly 6 monthly bars`() = runTest {
        setupEmptyMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(6, state.months.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `month labels are non-blank short names`() = runTest {
        // Labels are localized (locale-dependent), so assert shape rather than exact English text.
        setupEmptyMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            state.months.forEach { bar ->
                assertTrue("blank label", bar.label.isNotBlank())
                assertTrue("label too long: ${bar.label}", bar.label.length <= 5)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `months are in chronological order`() = runTest {
        setupEmptyMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            val indices = state.months.map { it.monthIndex }
            // monthIndex goes from older to newer — check that they're in cyclic order
            // Each consecutive pair should advance (mod 12)
            for (i in 0 until indices.size - 1) {
                val curr = indices[i]
                val next = indices[i + 1]
                val expectedNext = (curr + 1) % 12
                assertEquals("Month $i->${ i + 1}: expected $expectedNext got $next", expectedNext, next)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Empty state ──────────────────────────────────────────────────────────

    @Test
    fun `empty transactions produce zero total expenses`() = runTest {
        setupEmptyMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0L, state.totalExpenses.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty transactions produce zero total income`() = runTest {
        setupEmptyMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0L, state.totalIncome.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense change percent compares last two months`() = runTest {
        // Build a state directly: previous month 100.00, current 150.00 -> +50%.
        val cur = CurrencyCode("USD")
        val prev = MonthlyBar("M1", Money(10_000L, cur), Money(0L, cur), 0)
        val now = MonthlyBar("M2", Money(15_000L, cur), Money(20_000L, cur), 1)
        val state = AnalyticsUiState(months = listOf(prev, now))
        assertEquals(50, state.expenseChangePercent)
        // savings rate = (20000 - 15000) / 20000 = 25%
        assertEquals(25, state.savingsRatePercent)
    }

    @Test
    fun `insights are null without enough data`() = runTest {
        val state = AnalyticsUiState(months = emptyList())
        org.junit.Assert.assertNull(state.expenseChangePercent)
        org.junit.Assert.assertNull(state.savingsRatePercent)
    }

    @Test
    fun `maxValue is 1 when all months are zero`() = runTest {
        setupEmptyMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            // maxValue defaults to 1L to avoid division by zero in charts
            assertEquals(1L, state.maxValue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Expense aggregation ──────────────────────────────────────────────────

    @Test
    fun `expenses aggregated across all 6 months`() = runTest {
        val expense = Transaction(
            id = 1L, type = TransactionType.EXPENSE,
            accountId = 1L, categoryId = null,
            amountMinor = 5000L, currencyCode = "USD", accountAmountMinor = 5000L,
            occurredAt = now
        )
        every { userPreferences.baseCurrency } returns flowOf("USD")
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(expense))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            // Each month returns the same expense → 5000 × 6 = 30000
            assertEquals(30_000L, state.totalExpenses.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `income aggregated across all 6 months`() = runTest {
        val income = Transaction(
            id = 2L, type = TransactionType.INCOME,
            accountId = 1L, categoryId = null,
            amountMinor = 10_000L, currencyCode = "USD", accountAmountMinor = 10_000L,
            occurredAt = now
        )
        every { userPreferences.baseCurrency } returns flowOf("USD")
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(income))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(60_000L, state.totalIncome.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transfer transactions are excluded from totals`() = runTest {
        val transfer = Transaction(
            id = 3L, type = TransactionType.TRANSFER,
            accountId = 1L, categoryId = null,
            amountMinor = 5000L, currencyCode = "USD", accountAmountMinor = 5000L,
            occurredAt = now, transferGroupId = "g1"
        )
        every { userPreferences.baseCurrency } returns flowOf("USD")
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(transfer))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0L, state.totalExpenses.minor)
            assertEquals(0L, state.totalIncome.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each monthly bar tracks expenses separately`() = runTest {
        val expense = Transaction(
            id = 1L, type = TransactionType.EXPENSE,
            accountId = 1L, categoryId = null,
            amountMinor = 3_000L, currencyCode = "USD", accountAmountMinor = 3_000L,
            occurredAt = now
        )
        every { userPreferences.baseCurrency } returns flowOf("USD")
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(expense))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            state.months.forEach { bar ->
                assertEquals(3_000L, bar.expenses.minor)
                assertEquals(0L, bar.income.minor)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `maxValue reflects highest bar value`() = runTest {
        val income = Transaction(
            id = 2L, type = TransactionType.INCOME,
            accountId = 1L, categoryId = null,
            amountMinor = 9_999L, currencyCode = "USD", accountAmountMinor = 9_999L,
            occurredAt = now
        )
        every { userPreferences.baseCurrency } returns flowOf("USD")
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(income))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(9_999L, state.maxValue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `baseCurrency reflected in state`() = runTest {
        every { userPreferences.baseCurrency } returns flowOf("EUR")
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(emptyList())

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("EUR", state.baseCurrency)
            assertEquals("EUR", state.totalExpenses.currency.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mixed expenses and income tracked correctly per bar`() = runTest {
        val expense = Transaction(
            id = 1L, type = TransactionType.EXPENSE,
            accountId = 1L, categoryId = null,
            amountMinor = 4_000L, currencyCode = "USD", accountAmountMinor = 4_000L,
            occurredAt = now
        )
        val income = Transaction(
            id = 2L, type = TransactionType.INCOME,
            accountId = 1L, categoryId = null,
            amountMinor = 6_000L, currencyCode = "USD", accountAmountMinor = 6_000L,
            occurredAt = now
        )
        every { userPreferences.baseCurrency } returns flowOf("USD")
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(expense, income))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            state.months.forEach { bar ->
                assertEquals(4_000L, bar.expenses.minor)
                assertEquals(6_000L, bar.income.minor)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
