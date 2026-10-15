package com.personalmoney.core.sync

import com.personalmoney.core.auth.FakeTokenStore
import com.personalmoney.core.data.FakeKeyValueStore
import com.personalmoney.core.data.LocalDataWiper
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.database.Transactor
import com.personalmoney.core.database.dao.AccountDao
import com.personalmoney.core.database.dao.CategoryDao
import com.personalmoney.core.database.dao.CurrencyDao
import com.personalmoney.core.database.dao.SubscriptionDao
import com.personalmoney.core.database.dao.TransactionDao
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.household.HouseholdMembers
import com.personalmoney.core.network.CurrencyDto
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.HouseholdDto
import com.personalmoney.core.network.SyncPullResponseDto
import com.personalmoney.core.network.SyncPushResponseDto
import com.personalmoney.core.network.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A phone that sees its account in another household has to stop showing the old one's
 * ledger: it clears everything and pulls the new household's data from the start.
 */
class HouseholdChangeTest {

    private val epoch = "1970-01-01T00:00:00Z"
    private val oldCursor = "2026-05-01T00:00:00Z"
    private val newCursor = "2026-10-15T00:00:00Z"

    private val api = mockk<HomeBudgetApi>()
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val prefs = UserPreferences(FakeKeyValueStore())

    private val pulledSince = mutableListOf<String>()
    private var wipes = 0

    /** What the Room wiper does to preferences, without the database. */
    private val wiper = object : LocalDataWiper {
        override suspend fun wipe() {
            wipes++
            prefs.clearAccountScoped()
        }
    }

    private fun serverSays(householdId: Long?, name: String? = "Berkunsky family", baseCurrency: String? = null) {
        coEvery { api.me() } returns UserDto(
            id = 1, email = "a@example.com", householdId = householdId, householdName = name,
            householdRole = "MEMBER",
            baseCurrency = baseCurrency?.let { CurrencyDto(id = 9, code = it, name = it, symbol = it) },
        )
    }

    private fun engine(): SyncEngine {
        coEvery { api.pull(any(), any()) } answers {
            pulledSince += firstArg<String>()
            SyncPullResponseDto(serverTime = newCursor)
        }
        coEvery { api.push(any()) } returns SyncPushResponseDto(serverTime = newCursor)
        coEvery { api.household() } returns HouseholdDto(id = 2, name = "Berkunsky family")
        val transactor = object : Transactor {
            override suspend fun <R> transaction(block: suspend () -> R): R = block()
        }
        return SyncEngine(
            api = api,
            accountDao = accountDao,
            categoryDao = mockk<CategoryDao>(relaxed = true),
            transactionDao = mockk<TransactionDao>(relaxed = true),
            subscriptionDao = mockk<SubscriptionDao>(relaxed = true),
            currencyDao = mockk<CurrencyDao>(relaxed = true),
            transactor = transactor,
            prefs = prefs,
            tokenStore = FakeTokenStore(),
            wiper = wiper,
            members = HouseholdMembers(api),
        )
    }

    @Test
    fun `a different household wipes the phone, pulls from the start and leaves a notice`() = runTest {
        prefs.setHouseholdId(1)
        prefs.setSyncCursor(oldCursor)
        prefs.setAuthorBackfillDone(true)
        serverSays(householdId = 2)

        engine().syncNow()

        assertEquals(1, wipes)
        // The wipe blanks the cursor, so the pull that follows reads everything.
        assertEquals(listOf(epoch), pulledSince)
        assertEquals(2L, prefs.householdId.first())
        assertEquals("Berkunsky family", prefs.householdNotice.first())
        assertEquals(newCursor, prefs.syncCursor.first())
    }

    @Test
    fun `after a change the base currency is the new household's, not the default`() = runTest {
        prefs.setHouseholdId(1)
        prefs.setBaseCurrency("UAH")
        prefs.setOnboarded(true)
        prefs.setAuthorBackfillDone(true)
        serverSays(householdId = 2, baseCurrency = "EUR")

        engine().syncNow()

        assertEquals(1, wipes)
        assertEquals("EUR", prefs.baseCurrency.first())
    }

    @Test
    fun `edits not yet pushed belong to the old household and are not sent anywhere`() = runTest {
        prefs.setHouseholdId(1)
        prefs.setSyncCursor(oldCursor)
        // Not yet backfilled, so without the household check first this would push everything.
        // The edit is on the phone until the wipe takes it, as in the real database.
        coEvery { accountDao.pendingForSync(any()) } answers {
            if (wipes == 0) listOf(AccountEntity(name = "Cash", currencyCode = "USD")) else emptyList()
        }
        serverSays(householdId = 2)

        engine().syncNow()

        coVerify(exactly = 0) { api.push(any()) }
        assertEquals(1, wipes)
        // The backfill had nothing left to do, but is recorded so it is not asked for again.
        assertEquals(listOf(epoch), pulledSince)
        assertEquals(true, prefs.authorBackfillDone.first())
    }

    @Test
    fun `the first time a household is seen it is remembered, without a wipe`() = runTest {
        prefs.setSyncCursor(oldCursor)
        prefs.setAuthorBackfillDone(true)
        serverSays(householdId = 1)

        engine().syncNow()

        assertEquals(0, wipes)
        assertEquals(1L, prefs.householdId.first())
        assertNull(prefs.householdNotice.first())
        assertEquals(listOf(oldCursor), pulledSince)
    }

    @Test
    fun `the same household changes nothing`() = runTest {
        prefs.setHouseholdId(1)
        prefs.setSyncCursor(oldCursor)
        prefs.setAuthorBackfillDone(true)
        serverSays(householdId = 1)

        engine().syncNow()

        assertEquals(0, wipes)
        assertNull(prefs.householdNotice.first())
        assertEquals(listOf(oldCursor), pulledSince)
    }

    @Test
    fun `when the server cannot be asked the check is skipped and the sync goes on`() = runTest {
        prefs.setHouseholdId(1)
        prefs.setSyncCursor(oldCursor)
        prefs.setAuthorBackfillDone(true)
        coEvery { api.me() } throws java.io.IOException("offline")

        engine().syncNow()

        assertEquals(0, wipes)
        assertEquals(1L, prefs.householdId.first())
        assertEquals(listOf(oldCursor), pulledSince)
    }

    @Test
    fun `signing out forgets the household along with the rest`() = runTest {
        prefs.setHouseholdId(1)
        prefs.setHouseholdNotice("Home")

        prefs.clearAccountScoped()

        assertNull(prefs.householdId.first())
        assertNull(prefs.householdNotice.first())
    }
}
