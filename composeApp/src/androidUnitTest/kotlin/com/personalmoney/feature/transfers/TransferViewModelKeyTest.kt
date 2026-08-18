package com.personalmoney.feature.transfers

import app.cash.turbine.test
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.data.repository.TransactionRepository
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.ExchangeRateSource
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.personalmoney.core.calc.NumpadKey

/** Verifies that numpad-driven transfer entry builds a correct debit/credit pair. */
class TransferViewModelKeyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val cash = AccountEntity(id = 1L, name = "Cash", currencyCode = "USD")
    private val card = AccountEntity(id = 2L, name = "Card", currencyCode = "USD")
    private val euro = AccountEntity(id = 3L, name = "Euro", currencyCode = "EUR")

    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val exchangeRateRepository = mockk<ExchangeRateRepository>(relaxed = true)
    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)

    private fun usdToEur(rate: Double) = ExchangeRateEntity(
        baseCode = "USD", quoteCode = "EUR", rate = rate,
        date = LocalDate(2026, 5, 1), source = ExchangeRateSource.MANUAL, updatedAtEpochMs = 0
    )

    private fun viewModel(
        accounts: List<AccountEntity> = listOf(cash, card),
        rates: List<ExchangeRateEntity> = emptyList()
    ): TransferViewModel {
        every { accountRepository.observeActive() } returns flowOf(accounts)
        every { exchangeRateRepository.observeAll() } returns flowOf(rates)
        return TransferViewModel(accountRepository, exchangeRateRepository, transactionRepository)
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
            vm.onKey(TransferAmountField.TO, NumpadKey.DIGIT_0)     // 90 (manual)
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

    /** Regression: picking a foreign-currency account must not disable Save before a destination amount. */
    @Test
    fun `cross-currency keeps save enabled after only the source amount`() = runTest {
        val vm = viewModel(listOf(cash, euro))
        vm.uiState.test {
            var s = awaitItem()
            while (s.fromAccount == null || s.toAccount == null) s = awaitItem()
            while (s.sameCurrency) s = awaitItem()
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_5)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)   // 50, no destination typed
            while (s.amountFrom != "50") s = awaitItem()
            assertFalse(s.sameCurrency)
            assertTrue("Save must stay enabled in cross-currency before a destination amount", s.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** With a stored rate, the destination amount auto-fills and a transfer saves without manual entry. */
    @Test
    fun `cross-currency auto-converts the destination at the stored rate`() = runTest {
        val debit = slot<TransactionEntity>()
        val credit = slot<TransactionEntity>()
        coEvery {
            transactionRepository.insertTransferPair(capture(debit), capture(credit))
        } returns (1L to 2L)

        val vm = viewModel(listOf(cash, euro), rates = listOf(usdToEur(0.9)))
        vm.uiState.test {
            var s = awaitItem()
            while (s.fromAccount == null || s.toAccount == null) s = awaitItem()
            while (s.sameCurrency) s = awaitItem()
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_1)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)   // 100
            while (s.amountTo != "90.00") s = awaitItem()           // auto-converted at 0.9
            assertTrue(s.canSave)
            vm.save()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(-10000L, debit.captured.amountMinor)          // -$100.00
        assertEquals(9000L, credit.captured.amountMinor)           // +€90.00, no manual entry
        assertEquals("EUR", credit.captured.currencyCode)
    }

    /** Once the user types a destination amount, later source edits must not overwrite it. */
    @Test
    fun `manually editing the destination stops auto-conversion`() = runTest {
        val vm = viewModel(listOf(cash, euro), rates = listOf(usdToEur(0.9)))
        vm.uiState.test {
            var s = awaitItem()
            while (s.fromAccount == null || s.toAccount == null) s = awaitItem()
            while (s.sameCurrency) s = awaitItem()
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_1)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)   // 100 → auto 90.00
            while (s.amountTo != "90.00") s = awaitItem()
            vm.onKey(TransferAmountField.TO, NumpadKey.DIGIT_8)
            vm.onKey(TransferAmountField.TO, NumpadKey.DIGIT_5)     // user types 85
            while (s.amountTo != "85") s = awaitItem()
            vm.onKey(TransferAmountField.FROM, NumpadKey.DIGIT_0)   // source now 1000
            while (s.amountFrom != "1000") s = awaitItem()
            assertEquals("85", s.amountTo)                          // destination untouched
            cancelAndIgnoreRemainingEvents()
        }
    }
}
