package com.personalmoney.core.database.seed

import com.personalmoney.core.database.AppDatabase

class DatabaseSeeder constructor(
    private val db: AppDatabase
) {
    /**
     * Seeds default currencies and categories. Safe to call on every app start:
     * - Currencies use INSERT OR IGNORE so user edits are preserved.
     * - Categories are seeded only when the table is empty so user-archived defaults stay archived.
     *
     * Everything seeded here is marked **already synced**, which is what stops it being
     * pushed. These rows are the app's own starter content, not something the user
     * created: the server seeds its own defaults on registration, so uploading these as
     * well gave an account two overlapping sets - signing in on a second device turned
     * 8 categories into 32. They stay local until the user actually edits one, at which
     * point the repository marks it pending like any other change.
     */
    suspend fun seedIfEmpty() {
        db.currencyDao().insertAll(DefaultCurrencies.list.map { it.copy(pendingSync = false) })

        // Count the rows rather than probing for id 1. CategoryEntity's id is
        // autoGenerate, so the defaults only land on 1..24 the very first time a database
        // is created; once the table has been cleared - a sign-out, an account deletion, a
        // backup restore - SQLite carries on from where it left off and id 1 never comes
        // back. The old check therefore read "empty" forever after, and every launch
        // inserted another 24 categories: 24, then 48, then 72.
        if (db.categoryDao().countAll() == 0) {
            db.categoryDao().insertAll(DefaultCategories.list.map { it.copy(pendingSync = false) })
        }
    }
}
