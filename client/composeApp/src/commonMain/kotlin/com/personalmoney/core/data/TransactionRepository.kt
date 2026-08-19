package com.personalmoney.core.data

import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * The ledger, wherever it lives. See [AccountRepository] for the guarantees.
 *
 * Filtering is a repository concern rather than something callers do to a full list: on
 * Android it becomes a SQL WHERE, on the web it becomes query parameters, and neither
 * wants every row in memory to answer "last month, this account".
 */
interface TransactionRepository {

    fun observeRecent(limit: Int = 50): Flow<List<Transaction>>

    fun observeFiltered(
        accountId: Long? = null,
        categoryId: Long? = null,
        type: TransactionType? = null,
        from: Instant? = null,
        to: Instant? = null,
    ): Flow<List<Transaction>>

    /** One row, re-emitting on every change to it. */
    fun observeById(id: Long): Flow<Transaction?>

    suspend fun findById(id: Long): Transaction?

    /** Both legs of a transfer, by their shared [Transaction.transferGroupId]. */
    suspend fun findTransferPair(groupId: String): List<Transaction>

    suspend fun insert(transaction: Transaction): Long

    suspend fun update(transaction: Transaction)

    /**
     * Writes both legs of a transfer, or neither.
     *
     * A transfer is a signed debit and a signed credit sharing a [Transaction.transferGroupId];
     * saving one without the other would leave money that has left one account and arrived
     * nowhere. Android does it in a Room transaction, the web in one request the server
     * commits atomically - which is why this is a repository method rather than two inserts
     * at the call site.
     */
    suspend fun insertTransferPair(debit: Transaction, credit: Transaction)

    suspend fun delete(id: Long)

    /** Removes both legs. Deleting one alone would leave the other stranded. */
    suspend fun deleteTransfer(groupId: String)

    suspend fun refresh() {}
}
