package com.personalmoney.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: add an optional per-category monthly budget limit (base-currency minor units).
 * Nullable column, so existing rows default to NULL (no budget).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN budget_limit_minor INTEGER")
    }
}

/**
 * SQLite has no uuid() function, so a v4 uuid is assembled from random bytes.
 *
 * Version nibble forced to 4 and the variant nibble drawn from 8/9/a/b, so the values
 * are real v4 uuids rather than merely random-looking - the server stores them in a
 * column it treats as one.
 */
private const val UUID_EXPR = """
    lower(
        hex(randomblob(4)) || '-' ||
        hex(randomblob(2)) || '-4' ||
        substr(hex(randomblob(2)), 2) || '-' ||
        substr('89ab', abs(random()) % 4 + 1, 1) ||
        substr(hex(randomblob(2)), 2) || '-' ||
        hex(randomblob(6))
    )
"""

/** Tables that gained the sync columns in v3. */
private val SYNCED_TABLES =
    listOf("accounts", "categories", "transactions", "subscriptions", "currencies")

/**
 * v2 → v3: the app stops being local-only.
 *
 * Every table a user owns gains the four sync columns. Existing rows are given fresh
 * uuids and left marked `pending_sync`, so whatever is already on the phone is pushed
 * up on the first sync rather than being stranded or silently dropped.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in SYNCED_TABLES) {
            db.execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $table ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $table ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $table ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 1")
            // The column default has to be a constant, so the uuids are filled in here.
            db.execSQL("UPDATE $table SET uuid = $UUID_EXPR WHERE uuid = ''")
        }
        // `subscriptions` is deliberately absent: its entity never declared the two sync
        // indices, so schema v3 - the one a fresh v3 install actually creates, and the one
        // this migration is validated against - does not have them. Creating them here
        // anyway is what made an upgraded phone differ from a freshly installed one for
        // the same database version. [MIGRATION_3_4] is where that is put right, for both.
        for (table in SYNCED_TABLES - "subscriptions") {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${table}_uuid ON $table (uuid)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_pending_sync ON $table (pending_sync)")
        }
    }
}

/**
 * v3 → v4: `subscriptions` gets the two sync indices every other synced table has had
 * since v3.
 *
 * The unique index on `uuid` is the point. uuid is the identity a row is known by on the
 * server, and without the constraint nothing at the database level stops the same
 * subscription existing twice after a merge - which is the one thing sync must not do.
 *
 * `IF NOT EXISTS` is load-bearing rather than defensive: a phone that upgraded through the
 * *old* v2 → v3 already has both indices, a phone that installed fresh at v3 has neither,
 * and this has to leave the two of them identical.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_subscriptions_uuid ON subscriptions (uuid)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_subscriptions_pending_sync ON subscriptions (pending_sync)"
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
