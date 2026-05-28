package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.CurrencyDao
import com.personalmoney.core.database.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyRepository @Inject constructor(
    private val dao: CurrencyDao
) {
    fun observeActive(): Flow<List<CurrencyEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<CurrencyEntity>> = dao.observeAll()

    suspend fun findByCode(code: String): CurrencyEntity? = dao.findByCode(code)
    suspend fun setActive(code: String, active: Boolean) = dao.setActive(code, active)
    suspend fun create(currency: CurrencyEntity) = dao.insert(currency)
    suspend fun update(currency: CurrencyEntity) = dao.update(currency)
    suspend fun delete(code: String) = dao.deleteByCode(code)
    suspend fun usageCount(code: String): Int = dao.usageCount(code)
}
