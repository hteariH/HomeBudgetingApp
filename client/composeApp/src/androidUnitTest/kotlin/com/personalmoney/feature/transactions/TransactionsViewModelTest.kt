package com.personalmoney.feature.transactions

import app.cash.turbine.test
import com.personalmoney.core.data.LedgerFilterRequest
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.household.HouseholdMembers
import com.personalmoney.core.network.MemberDto
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = Clock.System.now()
    private val account = Account(id = 1L, name = "Credit Card", currencyCode = "USD")
    private val category = Category(id = 10L, name = "Coffee", type = CategoryType.EXPENSE, icon = "x", color = 0L)
    private val txCoffee = Transaction(
        id = 1L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = 10L,
        amountMinor = 1500L, currencyCode = "USD", accountAmountMinor = 1500L, occurredAt = now,
        note = "latte"
    )
    private val txRent = Transaction(
        id = 2L, type = TransactionType.EXPENSE, accountId = 1L, categoryId = null,
        amountMinor = 9900L, currencyCode = "USD", accountAmountMinor = 9900L, occurredAt = now,
        note = "rent"
    )

    private val txRepo = mockk<TransactionRepository>(relaxed = true)
    private val accRepo = mockk<AccountRepository>(relaxed = true)
    private val catRepo = mockk<CategoryRepository>(relaxed = true)

    private val householdMembers = mockk<HouseholdMembers>(relaxed = true)
    private val memberList = MutableStateFlow<List<MemberDto>>(emptyList())

    private fun vm(): TransactionsViewModel {
        every { householdMembers.members } returns memberList
        every { txRepo.observeFiltered(any(), any(), any(), any(), any()) } returns flowOf(listOf(txCoffee, txRent))
        every { accRepo.observeAll() } returns flowOf(listOf(account))
        every { catRepo.observeAll() } returns flowOf(listOf(category))
        return TransactionsViewModel(txRepo, accRepo, catRepo, LedgerFilterRequest(), householdMembers)
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

    @Test fun `duplicate inserts a copy with a new id`() = runTest {
        val vm = vm()
        vm.duplicate(txCoffee)
        coVerify {
            txRepo.insert(match { it.id == 0L && it.categoryId == 10L && it.accountAmountMinor == 1500L })
        }
    }

    @Test fun `duplicate is not attributed to the original's author`() = runTest {
        val vm = vm()
        vm.duplicate(txCoffee.copy(createdByUserId = 2L))
        coVerify { txRepo.insert(match { it.createdByUserId == null }) }
    }

    @Test fun `duplicate of an assistant entry is an app entry`() = runTest {
        val vm = vm()
        vm.duplicate(txCoffee.copy(source = "MCP"))
        coVerify { txRepo.insert(match { it.source == "APP" }) }
    }

    @Test fun `author initials appear once the household has two people`() = runTest {
        val vm = vm()
        vm.uiState.test {
            assertTrue(awaitItem().authorInitials.isEmpty())
            memberList.value = listOf(
                MemberDto(userId = 1L, firstName = "Anna", lastName = "Berkunsky"),
                MemberDto(userId = 2L, firstName = "Ivan", lastName = "Petrov"),
            )
            var s = awaitItem()
            while (s.authorInitials.isEmpty()) s = awaitItem()
            assertEquals(mapOf(1L to "AB", 2L to "IP"), s.authorInitials)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a household of one has no author initials`() = runTest {
        memberList.value = listOf(MemberDto(userId = 1L, firstName = "Anna", lastName = "Berkunsky"))
        val vm = vm()
        vm.uiState.test {
            var s = awaitItem()
            while (s.transactions.isEmpty()) s = awaitItem()
            assertTrue(s.authorInitials.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `duplicate ignores transfers`() = runTest {
        val vm = vm()
        vm.duplicate(txCoffee.copy(transferGroupId = "g1"))
        coVerify(exactly = 0) { txRepo.insert(any()) }
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
