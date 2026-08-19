package com.personalmoney.feature.accounts

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.core.domain.model.Account
import com.personalmoney.util.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accountRepo = mockk<AccountRepository>(relaxed = true)
    private val currencyRepo = mockk<CurrencyRepository>(relaxed = true)
    private val prefs = mockk<UserPreferences>(relaxed = true)

    private fun vm(defaultAccountId: Long? = 5L, txCount: Int = 3): AccountEditViewModel {
        every { currencyRepo.observeActive() } returns flowOf(emptyList())
        every { currencyRepo.observeAll() } returns flowOf(emptyList())
        coEvery { accountRepo.findById(5L) } returns Account(id = 5L, name = "Cash", currencyCode = "USD")
        coEvery { accountRepo.transactionCount(5L) } returns txCount
        coEvery { accountRepo.delete(5L) } just Runs
        every { prefs.defaultAccountId } returns flowOf(defaultAccountId)
        coEvery { prefs.setDefaultAccount(any()) } just Runs
        return AccountEditViewModel(
            SavedStateHandle(mapOf("accountId" to "5")), accountRepo, currencyRepo, prefs
        )
    }

    @Test
    fun `loads transaction count for the account`() = runTest {
        val vm = vm(txCount = 7)
        vm.uiState.test {
            var s = awaitItem()
            while (s.id == null) s = awaitItem()
            assertEquals(7, s.transactionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete clears default account when it points at this account`() = runTest {
        val vm = vm(defaultAccountId = 5L)
        vm.uiState.test {
            var s = awaitItem()
            while (s.id == null) s = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.delete()
        coVerify { accountRepo.delete(5L) }
        coVerify { prefs.setDefaultAccount(null) }
    }

    @Test
    fun `delete leaves default account untouched when it is a different account`() = runTest {
        val vm = vm(defaultAccountId = 99L)
        vm.uiState.test {
            var s = awaitItem()
            while (s.id == null) s = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.delete()
        coVerify { accountRepo.delete(5L) }
        coVerify(exactly = 0) { prefs.setDefaultAccount(any()) }
    }
}
