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
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.SyncPullResponseDto
import com.personalmoney.core.network.SyncPushResponseDto
import com.personalmoney.core.network.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transactions synced before schema v5 have no author, and an incremental pull never
 * resends them. The engine therefore re-reads everything once - but only after pushing
 * local edits, because a merge overwrites whatever row it meets.
 */
class SyncEngineAuthorBackfillTest {

    private val epoch = "1970-01-01T00:00:00Z"
    private val oldCursor = "2026-05-01T00:00:00Z"
    private val newCursor = "2026-10-15T00:00:00Z"

    private val api = mockk<HomeBudgetApi>()
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val prefs = UserPreferences(FakeKeyValueStore())

    private val pulledSince = mutableListOf<String>()

    private fun engine(): SyncEngine {
        coEvery { api.pull(any(), any()) } answers {
            pulledSince += firstArg<String>()
            SyncPullResponseDto(serverTime = newCursor)
        }
        coEvery { api.push(any()) } returns SyncPushResponseDto(serverTime = newCursor)
        // The household check runs first; this account stays in the one household throughout.
        coEvery { api.me() } returns UserDto(id = 1, email = "a@example.com", householdId = 1)
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
            wiper = object : LocalDataWiper {
                override suspend fun wipe() = error("the household did not change")
            },
            members = HouseholdMembers(api),
        )
    }

    /** One unsynced account edit, handed out once and then gone, as markSynced would leave it. */
    private fun withOnePendingAccount() {
        coEvery { accountDao.pendingForSync(any()) } returnsMany
            listOf(listOf(AccountEntity(name = "Cash", currencyCode = "USD")), emptyList())
    }

    @Test
    fun `a phone that synced before the upgrade pushes, then re-reads everything once`() = runTest {
        prefs.setSyncCursor(oldCursor)
        withOnePendingAccount()

        engine().syncNow()

        // The unsynced edit goes up before the pull that would otherwise overwrite it.
        coVerifyOrder {
            api.push(any())
            api.pull(epoch, any())
        }
        assertEquals(listOf(epoch), pulledSince)
        assertTrue(prefs.authorBackfillDone.first())
        // And the cursor is the new one, so the next sync is incremental again.
        assertEquals(newCursor, prefs.syncCursor.first())
    }

    @Test
    fun `once done, the next sync is incremental`() = runTest {
        prefs.setSyncCursor(oldCursor)
        prefs.setAuthorBackfillDone(true)

        engine().syncNow()

        assertEquals(listOf(oldCursor), pulledSince)
        coVerify(exactly = 0) { api.push(any()) }
    }

    @Test
    fun `a fresh install pulls from the start once and is never asked to again`() = runTest {
        val engine = engine()
        engine.syncNow()
        assertEquals(listOf(epoch), pulledSince)
        assertTrue(prefs.authorBackfillDone.first())

        engine.syncNow()

        assertEquals(listOf(epoch, newCursor), pulledSince)
    }

    @Test
    fun `signing out forgets the flag along with the cursor`() = runTest {
        prefs.setSyncCursor(oldCursor)
        prefs.setAuthorBackfillDone(true)

        prefs.clearAccountScoped()

        assertFalse(prefs.authorBackfillDone.first())
        assertEquals("", prefs.syncCursor.first())
    }
}
