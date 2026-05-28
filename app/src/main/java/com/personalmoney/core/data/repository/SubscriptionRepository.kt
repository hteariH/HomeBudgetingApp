package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.SubscriptionDao
import com.personalmoney.core.database.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val dao: SubscriptionDao
) {
    fun observeAll(): Flow<List<SubscriptionEntity>> = dao.observeAll()
    fun observeActive(): Flow<List<SubscriptionEntity>> = dao.observeActive()

    suspend fun findById(id: Long): SubscriptionEntity? = dao.findById(id)
    suspend fun findDueAsOf(date: LocalDate): List<SubscriptionEntity> =
        dao.findDueAsOf(date.toString())

    suspend fun create(subscription: SubscriptionEntity): Long = dao.insert(subscription)
    suspend fun update(subscription: SubscriptionEntity) = dao.update(subscription)
    suspend fun setActive(id: Long, active: Boolean) = dao.setActive(id, active)
    suspend fun setNextChargeDate(id: Long, date: LocalDate) =
        dao.setNextChargeDate(id, date.toString())
    suspend fun delete(id: Long) = dao.deleteById(id)
}
