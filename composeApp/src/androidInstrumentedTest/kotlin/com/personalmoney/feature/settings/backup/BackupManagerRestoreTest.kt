package com.personalmoney.feature.settings.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalmoney.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManagerRestoreTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManager(context, db)
    }

    @After
    fun teardown() = db.close()

    private fun sampleEnvelope() = BackupEnvelope(
        exportedAt = 0L,
        accounts = listOf(
            AccountDto(1, "Cash", "USD", 0, "wallet", 0xFF000000L, false, 0, true, null)
        ),
        categories = listOf(
            CategoryDto(10, "Food", "EXPENSE", "restaurant", 0xFF00FF00L, null, false, 0, false, 20_000L)
        ),
        transactions = listOf(
            TransactionDto(100, "EXPENSE", 1, 10, 500, "USD", 500, 1.0, 0, 0)
        ),
        subscriptions = emptyList(),
        currencies = listOf(CurrencyDto("USD", "US Dollar", "$", 2, true, 0)),
        exchangeRates = emptyList()
    )

    @Test
    fun restore_populates_database_and_preserves_budget() = runBlocking {
        val preview = manager.restore(sampleEnvelope())
        assertEquals(1, preview.accounts)
        assertEquals(1, preview.transactions)
        assertEquals(1, db.accountDao().observeAll().first().size)
        assertEquals(1, db.transactionDao().observeRecent(Int.MAX_VALUE).first().size)
        // Budget survives the round-trip.
        assertEquals(20_000L, db.categoryDao().findById(10)?.budgetLimitMinor)
    }

    @Test
    fun restore_is_idempotent_no_duplicate_or_crash() = runBlocking {
        manager.restore(sampleEnvelope())
        // Re-importing the same backup used to fail (ABORT) or duplicate; now it must be a no-op delta.
        manager.restore(sampleEnvelope())
        assertEquals(1, db.accountDao().observeAll().first().size)
        assertEquals(1, db.categoryDao().observeAll().first().size)
        assertEquals(1, db.transactionDao().observeRecent(Int.MAX_VALUE).first().size)
        assertEquals(1, db.currencyDao().observeAll().first().size)
    }
}
