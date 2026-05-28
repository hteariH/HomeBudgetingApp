package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY active DESC, next_charge_date ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE active = 1 ORDER BY next_charge_date ASC")
    fun observeActive(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE active = 1 AND next_charge_date <= :asOfDate")
    suspend fun findDueAsOf(asOfDate: String): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Query("UPDATE subscriptions SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE subscriptions SET next_charge_date = :next WHERE id = :id")
    suspend fun setNextChargeDate(id: Long, next: String)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
