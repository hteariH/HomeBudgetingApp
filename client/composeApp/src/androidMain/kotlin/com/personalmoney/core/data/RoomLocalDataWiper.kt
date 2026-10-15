package com.personalmoney.core.data

import androidx.room.withTransaction
import com.personalmoney.core.database.AppDatabase
import com.personalmoney.core.database.seed.DatabaseSeeder
import com.personalmoney.core.household.HouseholdMembers

/**
 * Empties the on-device ledger, then puts the starter content back.
 *
 * Re-seeding inside the same transaction rather than leaving the tables empty: the seed
 * is what gives a fresh account its currencies and its default categories, and it
 * otherwise only runs at application start ([com.personalmoney.PersonalMoneyApplication]).
 * Without this, signing out and straight back in on a device that is never killed in
 * between would land on a Quick Add screen with no categories and no currency to pick.
 *
 * Deletion order is children before parents, the same as
 * [com.personalmoney.feature.settings.backup.BackupManager]'s restore, because the foreign
 * keys point the same way.
 */
class RoomLocalDataWiper(
    private val db: AppDatabase,
    private val seeder: DatabaseSeeder,
    private val preferences: UserPreferences,
    private val members: HouseholdMembers,
) : LocalDataWiper {

    override suspend fun wipe() {
        db.withTransaction {
            db.transactionDao().deleteAll()
            db.subscriptionDao().deleteAll()
            db.categoryDao().deleteAll()
            db.accountDao().deleteAll()
            db.exchangeRateDao().deleteAll()
            db.currencyDao().deleteAll()
            seeder.seedIfEmpty()
        }
        preferences.clearAccountScoped()
        members.clear()
    }
}
