package com.personalmoney.core.database.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.personalmoney.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That seeding twice is not seeding twice as much.
 *
 * The seeder runs at every application start, so its emptiness check is the only thing
 * standing between the app and a category list that grows by 24 every launch. It used to
 * check for id 1, which only exists in a database that has never had its categories
 * cleared - so after a sign-out, an account deletion or a backup restore the check said
 * "empty" for ever and the defaults piled up. Caught on a device: 24, then 48.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSeederTest {

    private lateinit var db: AppDatabase
    private lateinit var seeder: DatabaseSeeder

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = DatabaseSeeder(db)
    }

    @After
    fun teardown() = db.close()

    @Test
    fun seedingAnEmptyDatabaseInsertsTheDefaultsOnce() = runBlocking {
        seeder.seedIfEmpty()

        assertEquals(DefaultCategories.list.size, db.categoryDao().countAll())
        assertEquals(DefaultCurrencies.list.size, db.currencyDao().countAll())
    }

    @Test
    fun seedingAgainChangesNothing() = runBlocking {
        seeder.seedIfEmpty()
        seeder.seedIfEmpty()
        seeder.seedIfEmpty()

        assertEquals(DefaultCategories.list.size, db.categoryDao().countAll())
        assertEquals(DefaultCurrencies.list.size, db.currencyDao().countAll())
    }

    @Test
    fun seedingAfterAWipeRestoresExactlyOneSetOfDefaults() = runBlocking {
        seeder.seedIfEmpty()

        // What signing out or deleting an account does, and the case the old id-1 check
        // could not survive: ids never return to 1 once the table has been emptied.
        db.categoryDao().deleteAll()
        seeder.seedIfEmpty()
        seeder.seedIfEmpty()

        assertEquals(DefaultCategories.list.size, db.categoryDao().countAll())
    }
}
