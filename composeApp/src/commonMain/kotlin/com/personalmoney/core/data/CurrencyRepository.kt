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

    suspend fun refresh() {}
}
