package com.personalmoney.core.data

import com.personalmoney.core.domain.model.Currency
import kotlinx.coroutines.flow.Flow

/** Currencies, wherever they live. See [AccountRepository] for the guarantees. */
interface CurrencyRepository {

    /** The ones the user has switched on, in their chosen order. */
    fun observeActive(): Flow<List<Currency>>

    fun observeAll(): Flow<List<Currency>>

    suspend fun findByCode(code: String): Currency?

    suspend fun setActive(code: String, active: Boolean)

    /** Returns the new currency's local id, where the platform has one. */
    suspend fun create(currency: Currency): Long

    suspend fun update(currency: Currency)

    /** How many accounts and transactions still hold this currency; blocks deleting one in use. */
    suspend fun usageCount(code: String): Int

    suspend fun delete(code: String)

    suspend fun refresh() {}
}
