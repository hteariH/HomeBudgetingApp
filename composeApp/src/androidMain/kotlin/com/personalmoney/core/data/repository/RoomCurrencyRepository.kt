package com.personalmoney.core.data.repository

import com.personalmoney.core.data.CurrencyRepository as SharedCurrencyRepository
import com.personalmoney.core.database.entity.CurrencyEntity
import com.personalmoney.core.domain.model.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The shared currency contract, backed by Room. */
class RoomCurrencyRepository(
    private val delegate: CurrencyRepository,
) : SharedCurrencyRepository {

    override fun observeActive(): Flow<List<Currency>> =
        delegate.observeActive().map { rows -> rows.map { it.toCurrency() } }

    override fun observeAll(): Flow<List<Currency>> =
        delegate.observeAll().map { rows -> rows.map { it.toCurrency() } }

    override suspend fun findByCode(code: String): Currency? =
        delegate.findByCode(code)?.toCurrency()

    override suspend fun setActive(code: String, active: Boolean) =
        delegate.setActive(code, active)
}

// serverId stays null: Room keys currencies on the code, and the device never learns the
// server's numeric id - sync travels by uuid and code, not by it.
private fun CurrencyEntity.toCurrency() = Currency(
    code = code,
    name = name,
    symbol = symbol,
    decimals = decimals,
    active = active,
    sortOrder = sortOrder,
)
