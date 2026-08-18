package com.personalmoney.core.data

import com.personalmoney.core.domain.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Reading and writing accounts, without saying where they live.
 *
 * The two implementations are genuinely different, not just different plumbing. Android
 * reads Room and syncs in the background, so its flows emit again whenever the database
 * changes and writes succeed offline. The web implementation calls REST, so its flows emit
 * once per refresh and a write needs the network. Screens are written against the weaker
 * of those guarantees.
 */
interface AccountRepository {

    /** Accounts the user has not archived, in their chosen order. */
    fun observeActive(): Flow<List<Account>>

    fun observeAll(): Flow<List<Account>>

    suspend fun findById(id: Long): Account?

    /** Returns the new account's local id. */
    suspend fun create(account: Account): Long

    suspend fun update(account: Account)

    suspend fun setArchived(id: Long, archived: Boolean)

    suspend fun delete(id: Long)

    /** Re-reads from the source. A no-op where reads are already live. */
    suspend fun refresh() {}
}
