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

/**
 * v2 → v3: the app stops being local-only.
 *
 * Every table a user owns gains the four sync columns. Existing rows are given fresh
 * uuids and left marked `pending_sync`, so whatever is already on the phone is pushed
 * up on the first sync rather than being stranded or silently dropped.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = listOf("accounts", "categories", "transactions", "subscriptions", "currencies")
        for (table in tables) {
            db.execSQL("ALTER TABLE $table ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $table ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $table ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE $table ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 1")
            // The column default has to be a constant, so the uuids are filled in here.
            db.execSQL("UPDATE $table SET uuid = $UUID_EXPR WHERE uuid = ''")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_${table}_uuid ON $table (uuid)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_pending_sync ON $table (pending_sync)")
        }
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
