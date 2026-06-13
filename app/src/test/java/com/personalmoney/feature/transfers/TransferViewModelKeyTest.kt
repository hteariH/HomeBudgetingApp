package com.personalmoney.feature.transfers

import android.content.Context
import app.cash.turbine.test
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.NumpadKey
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies that numpad-driven transfer entry builds a correct debit/credit pair. */
class TransferViewModelKeyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cash = AccountEntity(id = 1L, name = "Cash", currencyCode = "USD")
    private val card = AccountEntity(id = 2L, name = "Card", currencyCode = "USD")
    private val euro = AccountEntity(id = 3L, name = "Euro", currencyCode = "EUR")

    private val appContext = mockk<Context>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)

    private fun viewModel(accounts: List<AccountEntity> = listOf(cash, card)): TransferViewModel {
        every { accountRepository.observeActive() } returns flowOf(accounts)
        return TransferViewModel(appContext, accountRepository, transactionRepository)
    }

    @Test
    fun `same-currency transfer builds a correct debit-credit pair`() = runTest {
        val debit = slot<TransactionEntity>()
        val credit = slot<TransactionEntity>()
        coEvery {
            transactionRepository.insertTransferPair(capture(debit), capture(credit))
        } returns (1L to 2L)

        val vm = viewModel()
        vm.uiState.test {
            var s = awaitItem()
            while (s.fromAccount == null || s.toAccount == null) s = awaitItem()
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_5)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)
            while (s.amountFrom != "50") s = awaitItem()
            assertTrue(s.canSave)
            vm.save()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { transactionRepository.insertTransferPair(any(), any()) }
        assertEquals(TransactionType.TRANSFER, debit.captured.type)
        assertEquals(1L, debit.captured.accountId)               // from = Cash
        assertEquals(-5000L, debit.captured.amountMinor)         // -$50.00
        assertEquals(2L, debit.captured.transferCounterpartAccountId)
        assertEquals(2L, credit.captured.accountId)              // to = Card
        assertEquals(5000L, credit.captured.amountMinor)         // +$50.00 (mirrors source)
        assertEquals(1L, credit.captured.transferCounterpartAccountId)
        assertEquals(debit.captured.transferGroupId, credit.captured.transferGroupId)
    }

    @Test
    fun `fee first keypress replaces the default zero`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var s = awaitItem()
            while (s.fromAccount == null) s = awaitItem()
            assertEquals("0", s.feeFrom)
            vm.onKey(TransferAmountField.FEE, NumpadKey.DIGIT_3)
            while (s.feeFrom != "3") s = awaitItem()
            assertEquals("3", s.feeFrom)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fee is added to the source debit`() = runTest {
        val debit = slot<TransactionEntity>()
        coEvery {
            transactionRepository.insertTransferPair(capture(debit), any())
        } returns (1L to 2L)

        val vm = viewModel()
        vm.uiState.test {
            var s = awaitItem()
            while (s.fromAccount == null || s.toAccount == null) s = awaitItem()
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_5)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)   // 50
            vm.onKey(TransferAmountField.FEE, NumpadKey.DIGIT_2)    // fee 2 (replaces "0")
            while (s.amountFrom != "50" || s.feeFrom != "2") s = awaitItem()
            vm.save()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(-5200L, debit.captured.amountMinor)            // -($50.00 + $2.00)
        assertEquals(200L, debit.captured.transferFeeMinor)
    }

    @Test
    fun `cross-currency transfer uses both amounts`() = runTest {
        val debit = slot<TransactionEntity>()
        val credit = slot<TransactionEntity>()
        coEvery {
            transactionRepository.insertTransferPair(capture(debit), capture(credit))
        } returns (1L to 2L)

        val vm = viewModel(listOf(cash, euro))
        vm.uiState.test {
            var s = awaitItem()
            while (s.fromAccount == null || s.toAccount == null) s = awaitItem()
            while (s.sameCurrency) s = awaitItem()
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_1)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)   // 100
            vm.onKey(TransferAmountField.TO, NumpadKey.DIGIT_9)
            vm.onKey(TransferAmountField.TO, NumpadKey.DIGIT_0)     // 90
            while (s.amountFrom != "100" || s.amountTo != "90") s = awaitItem()
            assertTrue(s.canSave)
            vm.save()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(-10000L, debit.captured.amountMinor)          // -$100.00
        assertEquals("USD", debit.captured.currencyCode)
        assertEquals(9000L, credit.captured.amountMinor)           // +€90.00
        assertEquals("EUR", credit.captured.currencyCode)
    }
}
