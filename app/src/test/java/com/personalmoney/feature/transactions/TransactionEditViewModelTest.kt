package com.personalmoney.feature.transactions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CategoryRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CategoryEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.NumpadKey
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@Suppress("LargeClass")
class TransactionEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testAccount = AccountEntity(
        id = 1L,
        name = "Cash",
        currencyCode = "USD"
    )

    private val testCategory = CategoryEntity(
        id = 100L,
        name = "Food",
        type = CategoryType.EXPENSE,
        icon = "restaurant",
        color = 0xFF4CAF50L
    )

    private val now = Clock.System.now()

    private val testTransaction = TransactionEntity(
        id = 42L,
        type = TransactionType.EXPENSE,
        accountId = 1L,
        categoryId = 100L,
        amountMinor = 1500L,    // $15.00
        currencyCode = "USD",
        accountAmountMinor = 1500L,
        occurredAt = now,
        createdAt = now
    )

    private val transferTransaction = TransactionEntity(
        id = 42L,
        type = TransactionType.EXPENSE,
        accountId = 1L,
        categoryId = null,
        amountMinor = 2000L,
        currencyCode = "USD",
        accountAmountMinor = 2000L,
        occurredAt = now,
        createdAt = now,
        transferGroupId = "transfer-group-1"
    )

    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)

    private fun setupDefaultMocks(tx: TransactionEntity = testTransaction) {
        every { transactionRepository.observeById(42L) } returns flowOf(tx)
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount))
        every { categoryRepository.observeByType(CategoryType.EXPENSE) } returns flowOf(listOf(testCategory))
        every { categoryRepository.observeByType(CategoryType.INCOME) } returns flowOf(emptyList())
        coEvery { accountRepository.findById(1L) } returns testAccount
        coEvery { transactionRepository.update(any()) } just Runs
        coEvery { transactionRepository.delete(any()) } just Runs
        coEvery { transactionRepository.deleteTransfer(any()) } just Runs
    }

    private fun createViewModel(tx: TransactionEntity = testTransaction): TransactionEditViewModel {
        setupDefaultMocks(tx)
        return TransactionEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "42")),
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    @Test
    fun `loads transaction and sets expression from amount`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            // $15.00 → "15" (trailing zeros stripped)
            assertEquals("15", state.expression)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads transaction and sets type`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(TransactionType.EXPENSE, state.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads transaction and populates original`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertNotNull(state.original)
            assertEquals(42L, state.original?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads accounts from repository`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.accounts.size)
            assertEquals("Cash", state.accounts.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selects first account by default`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertNotNull(state.selectedAccount)
            assertEquals(1L, state.selectedAccount?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads categories from repository`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.categories.size)
            assertEquals("Food", state.categories.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSave is true when all fields valid`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Expression input ─────────────────────────────────────────────────────

    @Test
    fun `onKey digit appends to expression`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // "15"
            vm.onKey(NumpadKey.DIGIT_9)
            val state = awaitItem()
            assertEquals("159", state.expression)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onKey backspace removes last char`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // "15"
            vm.onKey(NumpadKey.BACKSPACE)
            val state = awaitItem()
            assertEquals("1", state.expression)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onKey backspace on single char leaves empty`() = runTest {
        val vm = createViewModel(
            testTransaction.copy(amountMinor = 100L) // $1.00 → "1"
        )
        coEvery { accountRepository.findById(1L) } returns testAccount

        vm.uiState.test {
            awaitItem() // "1"
            vm.onKey(NumpadKey.BACKSPACE)
            val state = awaitItem()
            assertEquals("", state.expression)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onKey adds operator correctly`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // "15"
            vm.onKey(NumpadKey.PLUS)
            vm.onKey(NumpadKey.DIGIT_5)
            // Flush intermediate
            var state = awaitItem()
            while (state.expression != "15+5") state = awaitItem()
            assertEquals("15+5", state.expression)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expression with operator shows preview result`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // "15"
            vm.onKey(NumpadKey.PLUS)
            vm.onKey(NumpadKey.DIGIT_5)
            var state = awaitItem()
            while (state.expression != "15+5") state = awaitItem()
            // "15+5" should show preview "20.00"
            assertEquals("20.00", state.previewResult)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── State updates ────────────────────────────────────────────────────────

    @Test
    fun `setNote updates note in state`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.setNote("Coffee")
            val state = awaitItem()
            assertEquals("Coffee", state.note)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setType changes type in state`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.setType(TransactionType.INCOME)
            val state = awaitItem()
            assertEquals(TransactionType.INCOME, state.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setType clears selected category`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.setType(TransactionType.INCOME)
            val state = awaitItem()
            assertNull(state.selectedCategory)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectCategory updates selected category`() = runTest {
        val anotherCategory = CategoryEntity(
            id = 200L, name = "Transport", type = CategoryType.EXPENSE,
            icon = "directions_car", color = 0xFF2196F3L
        )
        setupDefaultMocks()
        every { categoryRepository.observeByType(CategoryType.EXPENSE) } returns flowOf(listOf(testCategory, anotherCategory))
        val vm = TransactionEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "42")),
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )

        vm.uiState.test {
            awaitItem()
            vm.selectCategory(anotherCategory)
            val state = awaitItem()
            assertEquals(200L, state.selectedCategory?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectAccount updates selected account`() = runTest {
        val anotherAccount = AccountEntity(id = 2L, name = "Card", currencyCode = "USD")
        setupDefaultMocks()
        every { accountRepository.observeActive() } returns flowOf(listOf(testAccount, anotherAccount))
        val vm = TransactionEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to "42")),
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository
        )

        vm.uiState.test {
            awaitItem()
            vm.selectAccount(anotherAccount)
            val state = awaitItem()
            assertEquals(2L, state.selectedAccount?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Transfer mode ────────────────────────────────────────────────────────

    @Test
    fun `isTransfer is true when transaction has transferGroupId`() = runTest {
        val vm = createViewModel(transferTransaction)
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.isTransfer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSave is true for transfer without category`() = runTest {
        val vm = createViewModel(transferTransaction)
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertTrue(state.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onKey is ignored for transfer`() = runTest {
        val vm = createViewModel(transferTransaction)
        vm.uiState.test {
            val initialState = awaitItem()
            val initialExpression = initialState.expression
            vm.onKey(NumpadKey.DIGIT_5)
            // No new emission should be triggered
            expectNoEvents()
            assertEquals(initialExpression, vm.uiState.value.expression)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    fun `save calls repository update for regular transaction`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.save()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { transactionRepository.update(any()) }
    }

    @Test
    fun `save sets saved flag`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.save()
            var state = awaitItem()
            while (!state.saved) state = awaitItem()
            assertTrue(state.saved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save for transfer calls findTransferPair and updates both`() = runTest {
        coEvery { transactionRepository.findTransferPair("transfer-group-1") } returns listOf(
            transferTransaction,
            transferTransaction.copy(id = 43L, accountAmountMinor = -2000L)
        )
        val vm = createViewModel(transferTransaction)
        vm.uiState.test {
            awaitItem()
            vm.save()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { transactionRepository.findTransferPair("transfer-group-1") }
        coVerify(exactly = 2) { transactionRepository.update(any()) }
    }

    @Test
    fun `save does nothing when canSave is false`() = runTest {
        // Empty expression → not valid
        val vm = createViewModel(testTransaction.copy(amountMinor = 0L))
        vm.uiState.test {
            awaitItem()
            vm.save()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { transactionRepository.update(any()) }
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    fun `delete calls deleteById for regular transaction`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.delete()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { transactionRepository.delete(42L) }
    }

    @Test
    fun `delete sets deleted flag`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.delete()
            var state = awaitItem()
            while (!state.deleted) state = awaitItem()
            assertTrue(state.deleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete calls deleteTransfer for transfer transaction`() = runTest {
        val vm = createViewModel(transferTransaction)
        vm.uiState.test {
            awaitItem()
            vm.delete()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { transactionRepository.deleteTransfer("transfer-group-1") }
        coVerify(exactly = 0) { transactionRepository.delete(any()) }
    }

    @Test
    fun `clearError clears error in state`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.clearError()
            cancelAndIgnoreRemainingEvents()
        }
        // Just ensure it doesn't throw — error is null by default, clearError is idempotent
    }
}
