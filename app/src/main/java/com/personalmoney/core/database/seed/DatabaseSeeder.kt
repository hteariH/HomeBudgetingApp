package com.personalmoney.core.database.seed

import com.personalmoney.core.database.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
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

        val anyCategory = db.categoryDao().findById(1L)
        if (anyCategory == null) {
            db.categoryDao().insertAll(DefaultCategories.list.map { it.copy(pendingSync = false) })
        }
    }
}
