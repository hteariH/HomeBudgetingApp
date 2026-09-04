package com.personalmoney.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migrations, against the schemas Room recorded for each version.
 *
 * Two things have to hold, and neither is obvious from reading the DDL:
 *
 * - The resulting schema must match what Room expects. `runMigrationsAndValidate` is
 *   what proves it; a mismatched default or a missing index fails here rather than
 *   crashing on a user's device at launch.
 * - Rows that already existed must come out with real, distinct uuids and be marked
 *   pending, because whatever is already on the phone has to reach the server rather
 *   than being stranded locally or silently dropped.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_producesTheSchemaRoomExpects() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """
                INSERT INTO accounts (id, name, currency_code, initial_balance_minor, icon, color,
                                      archived, sort_order, include_in_total, note)
                VALUES (1, 'Cash', 'USD', 10000, 'wallet', 4280427161, 0, 0, 1, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO accounts (id, name, currency_code, initial_balance_minor, icon, color,
                                      archived, sort_order, include_in_total, note)
                VALUES (2, 'Savings', 'USD', 0, 'wallet', 4280427161, 0, 1, 1, NULL)
                """.trimIndent()
            )
            close()
        }

        // Validation is the point: it compares the migrated schema against schemas/3.json.
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        db.query("SELECT uuid, updated_at, deleted, pending_sync FROM accounts ORDER BY id").use { c ->
            assertEquals(2, c.count)
            val uuids = mutableListOf<String>()
            while (c.moveToNext()) {
                val uuid = c.getString(0)
                assertTrue("existing row got no uuid", uuid.isNotBlank())
                assertEquals("a never-synced row must have no server stamp", 0L, c.getLong(1))
                assertEquals("nothing was deleted", 0, c.getInt(2))
                assertEquals("existing data must be queued for upload", 1, c.getInt(3))
                uuids += uuid
            }
            // A shared uuid would make the two accounts collide on the server.
            assertNotEquals(uuids[0], uuids[1])
            assertEquals("uuid is not v4-shaped: ${uuids[0]}", 36, uuids[0].length)
        }
        db.close()
    }

    @Test
    fun migrate3To4_addsTheSubscriptionSyncIndices() {
        helper.createDatabase(dbName, 3).close()

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        assertTrue(
            "uuid must be unique, or a merge can leave the same subscription twice",
            hasIndex(db, "index_subscriptions_uuid", unique = true),
        )
        assertTrue(
            "the sync engine scans subscriptions by pending_sync",
            hasIndex(db, "index_subscriptions_pending_sync", unique = false),
        )
        db.close()
    }

    /**
     * The other v3 in the wild: a phone that upgraded through the old v2 → v3 already has
     * both indices. v4 has to be reachable from there too, rather than failing on a
     * duplicate-index error and leaving the app unable to open.
     */
    @Test
    fun migrate3To4_isReachableFromAv3ThatAlreadyHasTheIndices() {
        helper.createDatabase(dbName, 3).apply {
            execSQL("CREATE UNIQUE INDEX index_subscriptions_uuid ON subscriptions (uuid)")
            execSQL("CREATE INDEX index_subscriptions_pending_sync ON subscriptions (pending_sync)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)
        assertTrue(hasIndex(db, "index_subscriptions_uuid", unique = true))
        db.close()
    }

    private fun hasIndex(
        db: SupportSQLiteDatabase,
        name: String,
        unique: Boolean,
    ): Boolean = db.query("PRAGMA index_list(subscriptions)").use { c ->
        val nameColumn = c.getColumnIndexOrThrow("name")
        val uniqueColumn = c.getColumnIndexOrThrow("unique")
        while (c.moveToNext()) {
            if (c.getString(nameColumn) == name) {
                return@use (c.getInt(uniqueColumn) == 1) == unique
            }
        }
        false
    }
}
