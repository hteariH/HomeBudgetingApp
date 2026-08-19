package com.personalmoney.feature.onboarding

import app.cash.turbine.test
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.CurrencyRepository
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userPreferences = mockk<UserPreferences>(relaxed = true) {
        coEvery { setBaseCurrency(any()) } just Runs
        coEvery { setOnboarded(any()) } just Runs
    }
    private val accountRepository = mockk<AccountRepository>(relaxed = true) {
        coEvery { create(any()) } returns 1L
    }
    private val currencyRepository = mockk<CurrencyRepository>(relaxed = true) {
        every { observeActive() } returns flowOf(emptyList())
        every { observeAll() } returns flowOf(emptyList())
    }

    private fun createViewModel() = OnboardingViewModel(
        userPreferences = userPreferences,
        accountRepository = accountRepository,
        currencyRepository = currencyRepository
    )

    @Test
    fun `initial state has step 0 and USD currency`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0, state.step)
            assertEquals("USD", state.selectedCurrencyCode)
            assertEquals(3, state.totalSteps)
            assertFalse(state.saving)
            assertFalse(state.done)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectCurrency updates selectedCurrencyCode`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.selectCurrency("EUR")
            val state = awaitItem()
            assertEquals("EUR", state.selectedCurrencyCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAccountName updates accountName`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.setAccountName("Wallet")
            val state = awaitItem()
            assertEquals("Wallet", state.accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `next advances step from 0 to 1`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // step 0
            vm.next()
            val state = awaitItem()
            assertEquals(1, state.step)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `next on step 1 saves currency preference`() = runTest {
        val vm = createViewModel()
        vm.selectCurrency("GBP")
        vm.uiState.test {
            awaitItem() // step 0
            vm.next()  // advance to step 1
            awaitItem()
            vm.next()  // advance to step 2, should save "GBP"
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { userPreferences.setBaseCurrency("GBP") }
    }

    @Test
    fun `next does not advance past last step`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.next(); awaitItem()  // 0→1
            vm.next(); awaitItem()  // 1→2
            // next() at last step is a no-op — nothing emits
            vm.next()
            expectNoEvents()
            assertEquals(2, vm.uiState.value.step)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canProceed is true on step 0`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0, state.step)
            assertTrue(state.canProceed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canProceed is false on step 2 when accountName is blank`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.next(); awaitItem()  // to step 1
            vm.next()
            val state = awaitItem()  // to step 2
            assertEquals(2, state.step)
            assertEquals("", state.accountName)
            assertFalse(state.canProceed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canProceed is true on step 2 when accountName is set`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.next(); awaitItem()
            vm.next(); awaitItem()
            vm.setAccountName("Cash")
            val state = awaitItem()
            assertTrue(state.canProceed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `finish marks done and calls setOnboarded`() = runTest {
        val vm = createViewModel()
        vm.setAccountName("Cash")
        vm.uiState.test {
            awaitItem()
            vm.finish()
            var state = awaitItem()
            while (!state.done) state = awaitItem()
            assertTrue(state.done)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { userPreferences.setOnboarded(true) }
    }

    @Test
    fun `finish creates account when name is provided`() = runTest {
        val vm = createViewModel()
        vm.setAccountName("My Wallet")
        vm.uiState.test {
            awaitItem()
            vm.finish()
            var state = awaitItem()
            while (!state.done) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { accountRepository.create(any()) }
    }

    @Test
    fun `finish skips account creation when name is blank`() = runTest {
        val vm = createViewModel()
        // accountName stays blank
        vm.uiState.test {
            awaitItem()
            vm.finish()
            var state = awaitItem()
            while (!state.done) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { accountRepository.create(any()) }
    }

    @Test
    fun `skip calls finish and marks done`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            vm.skip()
            var state = awaitItem()
            while (!state.done) state = awaitItem()
            assertTrue(state.done)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { userPreferences.setOnboarded(true) }
    }
}
