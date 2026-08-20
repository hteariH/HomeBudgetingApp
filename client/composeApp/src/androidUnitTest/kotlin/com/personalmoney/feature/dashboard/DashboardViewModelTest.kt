package com.personalmoney.feature.dashboard

import app.cash.turbine.test
import com.personalmoney.core.data.LedgerFilterRequest
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.ExchangeRateRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.data.AccountBalance
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Clock.System.now()

    private val testAccount = Account(
        id = 1L,
        name = "Cash",
        currencyCode = "USD",
        includeInTotal = true
    )

    private val testCategory = Category(
        id = 100L,
        name = "Food",
        type = CategoryType.EXPENSE,
        icon = "restaurant",
        color = 0xFF4CAF50L
    )

    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val exchangeRateRepository = mockk<ExchangeRateRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true)

    private fun setupDefaultMocks(baseCurrency: String = "USD") {
        every { userPreferences.baseCurrency } returns flowOf(baseCurrency)
        every { exchangeRateRepository.observeAll() } returns flowOf(emptyList())
        every { accountRepository.observeActive() } returns flowOf(emptyList())
        every { categoryRepository.observeAll() } returns flowOf(emptyList())
        every { transactionRepository.observeRecent(any()) } returns flowOf(emptyList())
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(emptyList())
    }

    private fun createViewModel() = DashboardViewModel(
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRepository = transactionRepository,
        exchangeRateRepository = exchangeRateRepository,
        userPreferences = userPreferences,
        ledgerFilter = LedgerFilterRequest(),
    )

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial state has zero balance with no accounts`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0L, state.totalBalanceMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has zero monthly expenses`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0L, state.monthExpensesMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has zero monthly income`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0L, state.monthIncomeMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has empty recent transactions`() = runTest {
        setupDefaultMocks()
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.recent.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `baseCurrency reflected in state`() = runTest {
        setupDefaultMocks(baseCurrency = "EUR")
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("EUR", state.baseCurrency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Balance calculation ──────────────────────────────────────────────────

    @Test
    fun `total balance sums USD accounts without FX conversion`() = runTest {
        setupDefaultMocks()
        // Account with $100.00 balance (10000 minor units)
        every { accountRepository.observeActive() } returns
            flowOf(listOf(testAccount.copy(balanceMinor = 10_000L)))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(10_000L, state.totalBalanceMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `total balance sums multiple accounts`() = runTest {
        setupDefaultMocks()
        val account2 = Account(
            id = 2L, name = "Card", currencyCode = "USD",
            includeInTotal = true, balanceMinor = 3_000L,
        )
        every { accountRepository.observeActive() } returns flowOf(listOf(
            testAccount.copy(balanceMinor = 5_000L),
            account2
        ))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(8_000L, state.totalBalanceMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `account excluded from total when includeInTotal is false`() = runTest {
        setupDefaultMocks()
        // 9_999 on the excluded account: it must not reach the total.
        val excludedAccount = testAccount.copy(
            id = 2L, includeInTotal = false, balanceMinor = 9_999L,
        )
        every { accountRepository.observeActive() } returns flowOf(listOf(
            testAccount.copy(balanceMinor = 5_000L),
            excludedAccount
        ))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(5_000L, state.totalBalanceMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `account count reflects only include-in-total accounts`() = runTest {
        setupDefaultMocks()
        val excluded = testAccount.copy(id = 2L, includeInTotal = false)
        every { accountRepository.observeActive() } returns flowOf(listOf(
            testAccount,
            excluded
        ))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.totalAccountsCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Monthly stats ────────────────────────────────────────────────────────

    @Test
    fun `monthly expenses aggregated from current month transactions`() = runTest {
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        val expense = Transaction(
            id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = null,
            amountMinor = 2_500L, currencyCode = "USD", accountAmountMinor = 2_500L,
            occurredAt = now
        )
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(expense))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(2_500L, state.monthExpensesMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `monthly income aggregated from current month transactions`() = runTest {
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        val income = Transaction(
            id = 2L, type = TransactionType.INCOME, accountId = 1L, categoryId = null,
            amountMinor = 7_000L, currencyCode = "USD", accountAmountMinor = 7_000L,
            occurredAt = now
        )
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(income))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(7_000L, state.monthIncomeMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transfers excluded from monthly stats`() = runTest {
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        val transfer = Transaction(
            id = 3L, type = TransactionType.TRANSFER, accountId = 1L, categoryId = null,
            amountMinor = 5_000L, currencyCode = "USD", accountAmountMinor = 5_000L,
            occurredAt = now, transferGroupId = "g1"
        )
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(transfer))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0L, state.monthExpensesMinor)
            assertEquals(0L, state.monthIncomeMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expenses and income tracked separately`() = runTest {
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        val expense = Transaction(
            id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = null,
            amountMinor = 3_000L, currencyCode = "USD", accountAmountMinor = 3_000L,
            occurredAt = now
        )
        val income = Transaction(
            id = 2L, type = TransactionType.INCOME, accountId = 1L, categoryId = null,
            amountMinor = 8_000L, currencyCode = "USD", accountAmountMinor = 8_000L,
            occurredAt = now
        )
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(expense, income))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(3_000L, state.monthExpensesMinor)
            assertEquals(8_000L, state.monthIncomeMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Category breakdown ───────────────────────────────────────────────────

    @Test
    fun `expense breakdown slices created from category expenses`() = runTest {
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        every { categoryRepository.observeAll() } returns flowOf(listOf(testCategory))
        val expense = Transaction(
            id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 100L,
            amountMinor = 4_000L, currencyCode = "USD", accountAmountMinor = 4_000L,
            occurredAt = now
        )
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(expense))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.expenseByCategory.size)
            val slice = state.expenseByCategory.first()
            assertEquals(100L, slice.categoryId)
            assertEquals("Food", slice.name)
            assertEquals(4_000L, slice.totalMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `breakdown slices sorted descending by amount`() = runTest {
        setupDefaultMocks()
        val cat2 = testCategory.copy(id = 101L, name = "Transport")
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        every { categoryRepository.observeAll() } returns flowOf(listOf(testCategory, cat2))
        val e1 = Transaction(id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 100L,
            amountMinor = 1_000L, currencyCode = "USD", accountAmountMinor = 1_000L,
            fxRateToAccount = 1.0, occurredAt = now)
        val e2 = Transaction(id = 2L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 101L,
            amountMinor = 5_000L, currencyCode = "USD", accountAmountMinor = 5_000L,
            fxRateToAccount = 1.0, occurredAt = now)
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(e1, e2))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.expenseByCategory.size)
            // Highest first
            assertEquals(5_000L, state.expenseByCategory[0].totalMinor)
            assertEquals(1_000L, state.expenseByCategory[1].totalMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `breakdown capped at 6 categories`() = runTest {
        setupDefaultMocks()
        val categories = (1..8).map { i ->
            Category(id = i.toLong(), name = "Cat$i", type = CategoryType.EXPENSE, icon = "x", color = 0xFFL)
        }
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        every { categoryRepository.observeAll() } returns flowOf(categories)
        val txList = categories.mapIndexed { i, cat ->
            Transaction(
                id = i.toLong() + 1, type = TransactionType.EXPENSE, accountId = 1L,
                categoryId = cat.id, amountMinor = 1_000L, currencyCode = "USD",
                accountAmountMinor = 1_000L, occurredAt = now,
            )
        }
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(txList)

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.expenseByCategory.size <= 6)
            // 8 categories with spend -> 6 shown, 2 surfaced as "more".
            assertEquals(2, state.moreExpenseCategories)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Recent transactions ──────────────────────────────────────────────────

    @Test
    fun `recent transactions populated from repository`() = runTest {
        setupDefaultMocks()
        val tx = Transaction(
            id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = null,
            amountMinor = 500L, currencyCode = "USD", accountAmountMinor = 500L,
            occurredAt = now
        )
        every { transactionRepository.observeRecent(any()) } returns flowOf(listOf(tx))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.recent.size)
            assertEquals(1L, state.recent.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Net worth trend ──────────────────────────────────────────────────────

    @Test
    fun `net worth trend has six points reflecting balances`() = runTest {
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        coEvery { accountRepository.balancesAsOf(any()) } returns listOf(
            AccountBalance(1L, "USD", 5_000L)
        )
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(6, state.netWorthTrend.size)
            assertTrue(state.netWorthTrend.all { it == 5_000L }) // USD->USD, no FX
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Budgets ──────────────────────────────────────────────────────────────

    @Test
    fun `budgeted expense category produces budget progress with spent and limit`() = runTest {
        setupDefaultMocks()
        val budgetedCat = testCategory.copy(budgetLimitMinor = 10_000L) // $100 budget
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        every { categoryRepository.observeAll() } returns flowOf(listOf(budgetedCat))
        val expense = Transaction(
            id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 100L,
            amountMinor = 2_500L, currencyCode = "USD", accountAmountMinor = 2_500L,
            occurredAt = now
        )
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(expense))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.budgets.size)
            val b = state.budgets.first()
            assertEquals(100L, b.categoryId)
            assertEquals(2_500L, b.spentMinor)
            assertEquals(10_000L, b.limitMinor)
            assertTrue(!b.isOver)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `budget marked over when spend exceeds limit`() = runTest {
        setupDefaultMocks()
        val budgetedCat = testCategory.copy(budgetLimitMinor = 1_000L)
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        every { categoryRepository.observeAll() } returns flowOf(listOf(budgetedCat))
        val expense = Transaction(
            id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 100L,
            amountMinor = 5_000L, currencyCode = "USD", accountAmountMinor = 5_000L,
            occurredAt = now
        )
        every { transactionRepository.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(expense))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.budgets.size)
            assertTrue(state.budgets.first().isOver)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `categories without a budget produce no budget rows`() = runTest {
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        every { categoryRepository.observeAll() } returns flowOf(listOf(testCategory)) // no budget set
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.budgets.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `accounts and categories maps populated in state`() = runTest {
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        every { categoryRepository.observeAll() } returns flowOf(listOf(testCategory))

        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(testAccount, state.accountsById[1L])
            assertEquals(testCategory, state.categoriesById[100L])
            cancelAndIgnoreRemainingEvents()
        }
    }
}
