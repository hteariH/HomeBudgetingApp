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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
