package com.personalmoney.core.data

/**
 * Erases everything on this device that belonged to the account whose session is ending.
 *
 * An interface with a platform implementation for the same reason [KeyValueStore] is:
 * there is nothing worth sharing between them. Android keeps a full Room copy of the
 * ledger and has to empty and re-seed it; a browser tab keeps no local database at all,
 * so on the web there is nothing but preferences to clear.
 *
 * Used by both sign-out and account deletion, because the device ends up in the same
 * state either way - the only difference is whether the server still has a copy. Sign-out
 * needs it as much as deletion does: without it, signing in as a second person on the
 * same phone shows them the first person's transactions until sync happens to overwrite
 * them, which for a money app is the wrong failure by a wide margin.
 */
interface LocalDataWiper {

    suspend fun wipe()
}

/**
 * The whole of the job on a platform with no local database.
 *
 * Not an empty implementation: account-scoped preferences - the sync cursor, the cached
 * entitlement, the default account - are stored on the web too, and leaving them behind
 * would carry one account's state into the next one's session.
 */
class PreferencesLocalDataWiper(
    private val preferences: UserPreferences,
) : LocalDataWiper {

    override suspend fun wipe() {
        preferences.clearAccountScoped()
    }
}
