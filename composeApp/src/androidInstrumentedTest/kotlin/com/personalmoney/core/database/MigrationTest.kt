package com.personalmoney.core.database

import androidx.room.testing.MigrationTestHelper
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
 * Migration v2 → v3, the one that turns the local-only database into a synced one.
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
}
