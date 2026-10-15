package com.personalmoney.core.data

import app.cash.turbine.test
import com.personalmoney.feature.transactions.TransactionsViewModel
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.household.HouseholdMembers
import com.personalmoney.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Tapping an account on the overview opens the ledger showing that account.
 *
 * Worth a test because the obvious implementation - a navigation argument - was wrong in a
 * way that looked like an unrelated bug: it made the filtered ledger part of the overview's
 * saved back stack, so the Overview tab stopped responding. This is the behaviour that
 * replaced it, and the consume step is the half that is easy to drop.
 */
class LedgerFilterRequestTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val txRepo = mockk<TransactionRepository>(relaxed = true)
    private val accRepo = mockk<AccountRepository>(relaxed = true)
    private val catRepo = mockk<CategoryRepository>(relaxed = true)

    private val checking = Account(id = 7L, name = "Checking", currencyCode = "USD")
    private val savings = Account(id = 9L, name = "Savings", currencyCode = "USD")
    private val category =
        Category(id = 3L, name = "Coffee", type = CategoryType.EXPENSE, icon = "cup", color = 0L)

    private fun tx(id: Long, accountId: Long) = Transaction(
        id = id,
        accountId = accountId,
        categoryId = 3L,
        amountMinor = 450L,
        accountAmountMinor = 450L,
        currencyCode = "USD",
        type = TransactionType.EXPENSE,
        occurredAt = Clock.System.now(),
    )

    private fun vm(filter: LedgerFilterRequest): TransactionsViewModel {
        every { txRepo.observeFiltered(any(), any(), any(), any(), any()) } returns
            flowOf(listOf(tx(1L, 7L), tx(2L, 9L)))
        every { accRepo.observeAll() } returns flowOf(listOf(checking, savings))
        every { catRepo.observeAll() } returns flowOf(listOf(category))
        val members = mockk<HouseholdMembers>(relaxed = true)
        every { members.members } returns MutableStateFlow(emptyList())
        return TransactionsViewModel(txRepo, accRepo, catRepo, filter, members)
    }

    @Test
    fun `a requested account arrives as the ledger's filter`() = runTest {
        val filter = LedgerFilterRequest()
        filter.requestAccount(7L)

        vm(filter).uiState.test {
            var state = awaitItem()
            while (state.filter.accountId == null) state = awaitItem()

            assertEquals(7L, state.filter.accountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the request is taken, not left standing`() = runTest {
        val filter = LedgerFilterRequest()
        filter.requestAccount(7L)

        vm(filter).uiState.test {
            var state = awaitItem()
            while (state.filter.accountId == null) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        // Left set, it would re-narrow the list every time the tab came back into view,
        // undoing whatever the user had chosen there since.
        assertNull(filter.accountId.value)
    }

    @Test
    fun `no request means no filter`() = runTest {
        val filter = LedgerFilterRequest()

        vm(filter).uiState.test {
            val state = awaitItem()
            assertNull(state.filter.accountId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
