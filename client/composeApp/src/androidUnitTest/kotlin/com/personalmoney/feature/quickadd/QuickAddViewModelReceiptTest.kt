package com.personalmoney.feature.quickadd

import androidx.lifecycle.SavedStateHandle
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.receipt.ReceiptDraft
import com.personalmoney.core.receipt.ReceiptScanner
import com.personalmoney.core.receipt.ScanOutcome
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddViewModelReceiptTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val account = Account(id = 1L, name = "Cash", currencyCode = "UAH")
    private val food = Category(
        id = 100L, uuid = "cat-food", name = "Food", type = CategoryType.EXPENSE,
        icon = "restaurant", color = 0xFF4CAF50L,
    )
    private val other = Category(
        id = 101L, uuid = "cat-other", name = "Other", type = CategoryType.EXPENSE,
        icon = "tag", color = 0xFF4CAF50L,
    )

    private val accountRepository = mockk<AccountRepository>(relaxed = true).apply {
        every { observeActive() } returns flowOf(listOf(account))
    }
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true).apply {
        every { observeByType(any()) } returns flowOf(listOf(other, food))
        every { observeMostUsed(any(), any()) } returns flowOf(emptyList())
    }
    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferences>(relaxed = true).apply {
        every { defaultAccountId } returns flowOf(1L)
    }
    private val scanner = mockk<ReceiptScanner>()

    private fun viewModel() = QuickAddViewModel(
        SavedStateHandle(), accountRepository, categoryRepository, transactionRepository,
        userPreferences, scanner,
    )

    @Test
    fun `a read receipt fills the form and is saved with the entry`() = runTest {
        coEvery { scanner.canScan() } returns true
        coEvery { scanner.scan(any()) } returns ScanOutcome.Ready(
            ReceiptDraft(
                receiptUuid = "r-1",
                total = "249.90",
                date = LocalDate(2026, 10, 14),
                merchant = "Silpo",
                suggestedCategoryUuid = "cat-food",
            )
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertTrue("the household can scan", vm.scan.value.canScan)
        vm.scanReceipt(byteArrayOf(1, 2, 3))

        val state = vm.uiState.value
        assertEquals("249.90", state.expression)
        assertEquals(LocalDate(2026, 10, 14), state.date)
        assertEquals("Silpo", state.note)
        assertEquals(food, state.selectedCategory)
        assertFalse(vm.scan.value.reading)

        vm.submit()

        coVerify {
            transactionRepository.insert(match {
                it.receiptUuid == "r-1" && it.categoryId == 100L && it.amountMinor == 24_990L && it.note == "Silpo"
            })
        }
    }

    @Test
    fun `a failed read says why and leaves the form alone`() = runTest {
        coEvery { scanner.canScan() } returns true
        coEvery { scanner.scan(any()) } returns ScanOutcome.Failed("This model cannot read images")
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.scanReceipt(byteArrayOf(1))

        assertEquals(ScanNotice.Failed("This model cannot read images"), vm.scan.value.notice)
        assertEquals("", vm.uiState.value.expression)
    }

    @Test
    fun `the button stays hidden when the server cannot be asked`() = runTest {
        coEvery { scanner.canScan() } throws IllegalStateException("no server")

        val vm = viewModel()

        coVerify(exactly = 1) { scanner.canScan() }
        assertFalse(vm.scan.value.canScan)
    }

    @Test
    fun `a second tap after saving does not save the receipt twice`() = runTest {
        coEvery { scanner.canScan() } returns true
        coEvery { scanner.scan(any()) } returns ScanOutcome.Ready(
            ReceiptDraft(receiptUuid = "r-1", total = "10.00", suggestedCategoryUuid = "cat-food")
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.scanReceipt(byteArrayOf(1))

        vm.submit()
        vm.submit()

        coVerify(exactly = 1) { transactionRepository.insert(any()) }
        coVerify(exactly = 1) { transactionRepository.insert(match { it.receiptUuid == "r-1" }) }
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `the next entry starts without the receipt`() = runTest {
        coEvery { scanner.canScan() } returns true
        coEvery { scanner.scan(any()) } returns ScanOutcome.Ready(
            ReceiptDraft(receiptUuid = "r-1", total = "10.00", suggestedCategoryUuid = "cat-food")
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.scanReceipt(byteArrayOf(1))
        vm.submit()

        // The inline panel stays on screen: the next amount typed is a new entry.
        vm.setAmountText("5")
        vm.submit()

        coVerify(exactly = 1) { transactionRepository.insert(match { it.receiptUuid == "r-1" }) }
        coVerify(exactly = 1) { transactionRepository.insert(match { it.receiptUuid == null && it.amountMinor == 500L }) }
    }
}
