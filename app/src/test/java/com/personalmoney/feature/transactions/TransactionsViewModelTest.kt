package com.personalmoney.feature.transactions

import app.cash.turbine.test
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Clock.System.now()
    private val account = AccountEntity(id = 1L, name = "Credit Card", currencyCode = "USD")
    private val category = CategoryEntity(id = 10L, name = "Coffee", type = CategoryType.EXPENSE, icon = "x", color = 0L)
    private val txCoffee = TransactionEntity(
        id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 10L,
        amountMinor = 1500L, currencyCode = "USD", accountAmountMinor = 1500L, occurredAt = now, createdAt = now,
        note = "latte"
    )
    private val txRent = TransactionEntity(
        id = 2L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = null,
        amountMinor = 9900L, currencyCode = "USD", accountAmountMinor = 9900L, occurredAt = now, createdAt = now,
        note = "rent"
    )

    private val txRepo = mockk<TransactionRepository>(relaxed = true)
    private val accRepo = mockk<AccountRepository>(relaxed = true)
    private val catRepo = mockk<CategoryRepository>(relaxed = true)

    private fun vm(): TransactionsViewModel {
        every { txRepo.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(txCoffee, txRent))
        every { accRepo.observeAll() } returns flowOf(listOf(account))
        every { catRepo.observeAll() } returns flowOf(listOf(category))
        return TransactionsViewModel(txRepo, accRepo, catRepo)
    }

    @Test fun `search matches category name`() = runTest {
        val vm = vm()
        vm.setQuery("coffee")
        vm.uiState.test {
            var s = awaitItem()
            while (s.filter.query != "coffee") s = awaitItem()
            assertEquals(1, s.transactions.size)
            assertEquals(1L, s.transactions.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `search matches amount in major units`() = runTest {
        val vm = vm()
        vm.setQuery("99")
        vm.uiState.test {
            var s = awaitItem()
            while (s.filter.query != "99") s = awaitItem()
            assertEquals(1, s.transactions.size)
            assertEquals(2L, s.transactions.first().id) // 9900 minor -> "99.00"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `search matches account name`() = runTest {
        val vm = vm()
        vm.setQuery("credit")
        vm.uiState.test {
            var s = awaitItem()
            while (s.filter.query != "credit") s = awaitItem()
            assertEquals(2, s.transactions.size) // both on "Credit Card"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `clearFilters resets query and shows all`() = runTest {
        val vm = vm()
        vm.setQuery("coffee")
        vm.uiState.test {
            var s = awaitItem()
            while (s.filter.query != "coffee") s = awaitItem()
            vm.clearFilters()
            while (s.filter.query.isNotEmpty()) s = awaitItem()
            assertEquals(2, s.transactions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
