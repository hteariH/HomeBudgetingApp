package com.personalmoney.core.homemoney

import com.personalmoney.core.currency.FxScheduler
import com.personalmoney.core.data.repository.AccountRepository
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.database.Transactor
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.database.entity.CurrencyEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMoneyRepositoryTest {

    private val api = mockk<HomeMoneyApi>(relaxed = true)
    private val accountRepo = mockk<AccountRepository>(relaxed = true)
    private val currencyRepo = mockk<CurrencyRepository>(relaxed = true)
    private val fxScheduler = mockk<FxScheduler>(relaxed = true)

    // Run the transaction block inline — no real Room database needed.
    private val transactor = object : Transactor {
        override suspend fun <R> transaction(block: suspend () -> R): R = block()
    }

    private fun repo() = HomeMoneyRepository(api, accountRepo, currencyRepo, transactor, fxScheduler)

    private fun candidate(
        name: String,
        code: String,
        minor: Long,
        include: Boolean = true,
    ) = ImportCandidate(
        displayName = name,
        currencyCode = code,
        balanceMinor = minor,
        includeInTotal = include,
        groupName = null,
        alreadyExists = false,
        defaultSelected = true,
    )

    @Test
    fun `import appends sortOrder after the highest existing and maps fields`() = runTest {
        coEvery { accountRepo.observeAll() } returns flowOf(
            listOf(AccountEntity(id = 1, name = "Old", currencyCode = "USD", sortOrder = 4))
        )
        coEvery { currencyRepo.findByCode(any()) } returns null
        val created = mutableListOf<AccountEntity>()
        coEvery { accountRepo.create(capture(created)) } returns 10L

        val count = repo().import(
            listOf(
                candidate("Wallet", "UAH", 123456L, include = true),
                candidate("Card", "EUR", 2000L, include = false),
            )
        )

        assertEquals(2, count)
        assertEquals(5, created[0].sortOrder)
        assertEquals(6, created[1].sortOrder)
        assertEquals(123456L, created[0].initialBalanceMinor)
        assertEquals("UAH", created[0].currencyCode)
        assertEquals(false, created[1].includeInTotal)
        coVerify { fxScheduler.refreshNow() }
    }

    @Test
    fun `import creates a missing currency and reactivates an inactive one`() = runTest {
        coEvery { accountRepo.observeAll() } returns flowOf(emptyList())
        coEvery { accountRepo.create(any()) } returns 1L
        coEvery { currencyRepo.findByCode("UAH") } returns null
        coEvery { currencyRepo.findByCode("EUR") } returns
            CurrencyEntity(code = "EUR", name = "Euro", symbol = "€", active = false)

        repo().import(listOf(candidate("A", "UAH", 1L), candidate("B", "EUR", 1L)))

        coVerify { currencyRepo.create(match { it.code == "UAH" && it.active }) }
        coVerify { currencyRepo.setActive("EUR", true) }
    }

    @Test
    fun `import skips currency work for an already-active currency`() = runTest {
        coEvery { accountRepo.observeAll() } returns flowOf(emptyList())
        coEvery { accountRepo.create(any()) } returns 1L
        coEvery { currencyRepo.findByCode("USD") } returns
            CurrencyEntity(code = "USD", name = "US Dollar", symbol = "$", active = true)

        repo().import(listOf(candidate("A", "USD", 1L)))

        coVerify(exactly = 0) { currencyRepo.create(any()) }
        coVerify(exactly = 0) { currencyRepo.setActive(any(), any()) }
    }

    @Test
    fun `empty import does nothing`() = runTest {
        assertEquals(0, repo().import(emptyList()))
        coVerify(exactly = 0) { fxScheduler.refreshNow() }
    }
}
