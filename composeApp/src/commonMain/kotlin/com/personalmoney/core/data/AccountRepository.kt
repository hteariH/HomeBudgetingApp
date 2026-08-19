package com.personalmoney.core.data

import com.personalmoney.core.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

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

    /**
     * Every account's balance as it stood at [asOf], in that account's own currency.
     *
     * Reconstructed from history rather than stored, so no snapshot can go stale or
     * disagree with the ledger. Android answers it with one SQL sum; the web works it out
     * from the transactions it has already pulled, which is why the result is a plain list
     * rather than a flow.
     */
    suspend fun balancesAsOf(asOf: Instant): List<AccountBalance>

    /**
     * How many ledger rows sit on this account.
     *
     * The delete confirmation needs it: removing an account takes its transactions with
     * it, and the user should be told how many before that happens.
     */
    suspend fun transactionCount(id: Long): Int

    /** Persists a new display order, given the account ids in the order the user dragged them into. */
    suspend fun reorder(ids: List<Long>)

    suspend fun delete(id: Long)

    /** Re-reads from the source. A no-op where reads are already live. */
    suspend fun refresh() {}
}

/** One account's balance at a point in time, in that account's currency. */
data class AccountBalance(
    val accountId: Long,
    val currencyCode: String,
    val balanceMinor: Long,
)
