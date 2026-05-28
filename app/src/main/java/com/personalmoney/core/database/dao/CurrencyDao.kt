package com.personalmoney.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalmoney.core.database.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies WHERE active = 1 ORDER BY sortOrder, code")
    fun observeActive(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies ORDER BY active DESC, sortOrder, code")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): CurrencyEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(currency: CurrencyEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(currencies: List<CurrencyEntity>): List<Long>

    @Update
    suspend fun update(currency: CurrencyEntity)

    @Query("UPDATE currencies SET active = :active WHERE code = :code")
    suspend fun setActive(code: String, active: Boolean)

    @Query("DELETE FROM currencies WHERE code = :code")
    suspend fun deleteByCode(code: String)

    @Query(
        """
        SELECT (
            (SELECT COUNT(*) FROM accounts WHERE currency_code = :code) +
            (SELECT COUNT(*) FROM transactions WHERE currency_code = :code) +
            (SELECT COUNT(*) FROM subscriptions WHERE currency_code = :code)
        )
        """
    )
    suspend fun usageCount(code: String): Int
}
