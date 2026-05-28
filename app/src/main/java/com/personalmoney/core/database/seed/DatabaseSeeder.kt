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
     */
    suspend fun seedIfEmpty() {
        db.currencyDao().insertAll(DefaultCurrencies.list)

        val anyCategory = db.categoryDao().findById(1L)
        if (anyCategory == null) {
            db.categoryDao().insertAll(DefaultCategories.list)
        }
    }
}
