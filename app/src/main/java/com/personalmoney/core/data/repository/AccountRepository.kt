package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.AccountBalanceRow
import com.personalmoney.core.database.dao.AccountDao
import com.personalmoney.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val dao: AccountDao
) {
    fun observeActive(): Flow<List<AccountEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<AccountEntity>> = dao.observeAll()
    fun observeById(id: Long): Flow<AccountEntity?> = dao.observeById(id)
    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()
    fun observeBalances(): Flow<List<AccountBalanceRow>> = dao.observeBalances()
    suspend fun balancesAsOf(asOfMs: Long): List<AccountBalanceRow> = dao.balancesAsOf(asOfMs)

    suspend fun findById(id: Long): AccountEntity? = dao.findById(id)

    suspend fun create(account: AccountEntity): Long = dao.insert(account)
    suspend fun update(account: AccountEntity) = dao.update(account)
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> dao.updateSortOrder(id, index) }
    }
}
