package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.CategoryTotalRow
import com.personalmoney.core.database.dao.TransactionDao
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** Public surface unchanged; writes now mark rows for sync and deletes leave tombstones. */
class TransactionRepository constructor(
    private val dao: TransactionDao,
    private val syncTrigger: SyncTrigger
) {
    fun observeRecent(limit: Int = 50): Flow<List<TransactionEntity>> = dao.observeRecent(limit)
    fun observeById(id: Long): Flow<TransactionEntity?> = dao.observeById(id)

    fun observeFiltered(
        from: Instant? = null,
        to: Instant? = null,
        accountId: Long? = null,
        categoryId: Long? = null,
        type: TransactionType? = null
    ): Flow<List<TransactionEntity>> = dao.observeFiltered(
        from?.toEpochMilliseconds(), to?.toEpochMilliseconds(), accountId, categoryId, type?.name
    )

    fun observeCategoryTotals(
        type: TransactionType,
        from: Instant,
        to: Instant
    ): Flow<List<CategoryTotalRow>> =
        dao.observeCategoryTotals(type.name, from.toEpochMilliseconds(), to.toEpochMilliseconds())

    fun observeSumByTypeInAccount(
        type: TransactionType,
        accountId: Long,
        from: Instant,
        to: Instant
    ): Flow<Long> =
        dao.observeSumByTypeInAccount(type.name, accountId, from.toEpochMilliseconds(), to.toEpochMilliseconds())

    suspend fun findById(id: Long): TransactionEntity? = dao.findById(id)

    suspend fun insert(transaction: TransactionEntity): Long =
        dao.insert(transaction.copy(pendingSync = true)).also { syncTrigger.requestSync() }

    suspend fun update(transaction: TransactionEntity) {
        dao.update(transaction.copy(pendingSync = true))
        syncTrigger.requestSync()
    }

    suspend fun delete(id: Long) {
        dao.tombstoneById(id)
        syncTrigger.requestSync()
    }

    /** Both legs go together: half a transfer is two wrong balances. */
    suspend fun deleteTransfer(groupId: String) {
        dao.tombstoneByTransferGroup(groupId)
        syncTrigger.requestSync()
    }

    suspend fun insertTransferPair(debit: TransactionEntity, credit: TransactionEntity) =
        dao.insertTransferPair(debit.copy(pendingSync = true), credit.copy(pendingSync = true))
            .also { syncTrigger.requestSync() }

    suspend fun findTransferPair(groupId: String): List<TransactionEntity> = dao.findTransferPair(groupId)
}
