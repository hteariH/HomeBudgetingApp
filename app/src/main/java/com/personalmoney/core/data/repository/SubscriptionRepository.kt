package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.SubscriptionDao
import com.personalmoney.core.database.entity.SubscriptionEntity
import com.personalmoney.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Public surface unchanged; writes now mark rows for sync and deletes leave tombstones. */
@Singleton
class SubscriptionRepository @Inject constructor(
    private val dao: SubscriptionDao,
    private val syncTrigger: SyncTrigger
) {
    fun observeAll(): Flow<List<SubscriptionEntity>> = dao.observeAll()
    fun observeActive(): Flow<List<SubscriptionEntity>> = dao.observeActive()

    suspend fun findById(id: Long): SubscriptionEntity? = dao.findById(id)
    suspend fun findDueAsOf(date: LocalDate): List<SubscriptionEntity> =
        dao.findDueAsOf(date.toString())

    suspend fun create(subscription: SubscriptionEntity): Long =
        dao.insert(subscription.copy(pendingSync = true)).also { syncTrigger.requestSync() }

    suspend fun update(subscription: SubscriptionEntity) {
        dao.update(subscription.copy(pendingSync = true))
        syncTrigger.requestSync()
    }

    suspend fun setActive(id: Long, active: Boolean) {
        dao.setActive(id, active)
        syncTrigger.requestSync()
    }

    suspend fun setNextChargeDate(id: Long, date: LocalDate) {
        dao.setNextChargeDate(id, date.toString())
        syncTrigger.requestSync()
    }

    suspend fun delete(id: Long) {
        dao.tombstoneById(id)
        syncTrigger.requestSync()
    }
}
