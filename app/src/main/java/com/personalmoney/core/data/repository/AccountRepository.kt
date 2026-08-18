package com.personalmoney.core.data.repository

import com.personalmoney.core.database.Transactor
import com.personalmoney.core.database.dao.AccountBalanceRow
import com.personalmoney.core.database.dao.AccountDao
import com.personalmoney.core.database.entity.AccountEntity
import com.personalmoney.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The public surface is unchanged from the local-only version on purpose: every screen
 * and ViewModel keeps calling exactly what it called before. What changed is underneath -
 * a write now marks the row for sync, and a delete leaves a tombstone instead of a hole.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val transactor: Transactor,
    private val syncTrigger: SyncTrigger
) {
    fun observeActive(): Flow<List<AccountEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<AccountEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<AccountEntity?> = dao.observeById(id)
    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()
    fun observeBalances(): Flow<List<AccountBalanceRow>> = dao.observeBalances()
    suspend fun balancesAsOf(asOfMs: Long): List<AccountBalanceRow> = dao.balancesAsOf(asOfMs)

    suspend fun findById(id: Long): AccountEntity? = dao.findById(id)
    suspend fun transactionCount(id: Long): Int = dao.transactionCount(id)

    suspend fun create(account: AccountEntity): Long =
        dao.insert(account.copy(pendingSync = true)).also { syncTrigger.requestSync() }

    // pendingSync is forced rather than trusted: callers build the updated entity with
    // .copy() from one that may already have been synced, which would leave the flag off.
    suspend fun update(account: AccountEntity) {
        dao.update(account.copy(pendingSync = true))
        syncTrigger.requestSync()
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        dao.setArchived(id, archived)
        syncTrigger.requestSync()
    }

    /**
     * Tombstones the account together with its transactions and subscriptions.
     *
     * The FK cascade only fires on a real DELETE, so without walking the children they
     * would stay visible and keep counting towards totals.
     */
    suspend fun delete(id: Long) {
        transactor.transaction {
            dao.tombstoneTransactionsOf(id)
            dao.tombstoneSubscriptionsOf(id)
            dao.tombstoneById(id)
        }
        syncTrigger.requestSync()
    }

    suspend fun reorder(ids: List<Long>) {
        transactor.transaction {
            ids.forEachIndexed { index, id -> dao.updateSortOrder(id, index) }
        }
        syncTrigger.requestSync()
    }
}
