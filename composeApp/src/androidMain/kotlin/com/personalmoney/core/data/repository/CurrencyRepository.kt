package com.personalmoney.core.data.repository

import com.personalmoney.core.database.dao.CurrencyDao
import com.personalmoney.core.database.entity.CurrencyEntity
import com.personalmoney.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow

/**
 * The catalog is reference data; what syncs is which entries the user switched on.
 * That is why toggling `active` marks the row pending but the catalog itself is never
 * pushed as new content.
 */
class CurrencyRepository constructor(
    private val dao: CurrencyDao,
    private val syncTrigger: SyncTrigger
) {
    fun observeActive(): Flow<List<CurrencyEntity>> = dao.observeActive()
    fun observeAll(): Flow<List<CurrencyEntity>> = dao.observeAll()

    suspend fun findByCode(code: String): CurrencyEntity? = dao.findByCode(code)

    suspend fun setActive(code: String, active: Boolean) {
        dao.setActive(code, active)
        syncTrigger.requestSync()
    }

    suspend fun create(currency: CurrencyEntity) {
        dao.insert(currency.copy(pendingSync = true))
        syncTrigger.requestSync()
    }

    suspend fun update(currency: CurrencyEntity) {
        dao.update(currency.copy(pendingSync = true))
        syncTrigger.requestSync()
    }

    suspend fun delete(code: String) {
        dao.tombstoneByCode(code)
        syncTrigger.requestSync()
    }

    suspend fun usageCount(code: String): Int = dao.usageCount(code)
}
