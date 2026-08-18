package com.personalmoney.core.data

import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

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

    suspend fun findById(id: Long): Transaction?

    suspend fun insert(transaction: Transaction): Long

    suspend fun update(transaction: Transaction)

    suspend fun delete(id: Long)

    suspend fun refresh() {}
}
