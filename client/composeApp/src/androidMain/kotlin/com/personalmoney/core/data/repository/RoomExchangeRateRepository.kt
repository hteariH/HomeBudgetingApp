package com.personalmoney.core.data.repository

import com.personalmoney.core.currency.FxRate
import com.personalmoney.core.currency.toFxRates
import com.personalmoney.core.data.ExchangeRateRepository as SharedExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The shared rate contract, backed by Room.
 *
 * No [refresh]: rows arrive from `FxRefreshWorker`, and Room's flow re-emits the moment
 * they land, so there is nothing for a caller to pull.
 */
class RoomExchangeRateRepository(
    private val delegate: ExchangeRateRepository,
) : SharedExchangeRateRepository {

    override fun observeAll(): Flow<List<FxRate>> =
        delegate.observeAll().map { it.toFxRates() }
}
