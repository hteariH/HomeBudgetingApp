package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.CategoryTotalRow
import com.personalmoney.core.database.dao.TransactionDao
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao
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
    suspend fun insert(transaction: TransactionEntity): Long = dao.insert(transaction)
    suspend fun update(transaction: TransactionEntity) = dao.update(transaction)
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun deleteTransfer(groupId: String) = dao.deleteByTransferGroup(groupId)
    suspend fun insertTransferPair(debit: TransactionEntity, credit: TransactionEntity) =
        dao.insertTransferPair(debit, credit)
    suspend fun findTransferPair(groupId: String): List<TransactionEntity> = dao.findTransferPair(groupId)
}
