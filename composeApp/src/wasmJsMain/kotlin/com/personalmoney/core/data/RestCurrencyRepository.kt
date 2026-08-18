package com.personalmoney.core.data

import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.model.Currency
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The shared currency contract, backed by REST. See [RestAccountRepository].
 *
 * Refreshing also feeds [CurrencyRegistry], because money formatting reads decimals from
 * there synchronously and would otherwise fall back to two for a currency the user holds.
 */
class RestCurrencyRepository(private val api: RestApi) : CurrencyRepository {

    private val cache = MutableStateFlow<List<Currency>>(emptyList())

    override fun observeActive(): Flow<List<Currency>> =
        cache.map { all -> all.filter { it.active }.sortedBy { it.sortOrder } }

    override fun observeAll(): Flow<List<Currency>> = cache

    override suspend fun findByCode(code: String): Currency? =
        cache.value.firstOrNull { it.code == code }

    override suspend fun setActive(code: String, active: Boolean) {
        // The catalogue is global and admin-owned on the server; a user does not switch
        // entries on and off, they pick a base currency. Nothing to call here.
    }

    override suspend fun refresh() {
        val remote = api.currencies().map { it.toCurrency() }
        CurrencyRegistry.update(
            remote.map { CurrencyRegistry.Info(it.code, it.symbol, it.decimals) }
        )
        cache.value = remote
    }
}

private fun RestCurrency.toCurrency() = Currency(
    code = code,
    name = name,
    symbol = symbol,
    decimals = decimals,
    // Every currency the server returns is one this user can hold.
    active = true,
    sortOrder = sortOrder,
    serverId = id,
)
