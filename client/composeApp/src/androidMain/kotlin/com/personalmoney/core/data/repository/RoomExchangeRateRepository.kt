package com.personalmoney.core.data.repository

import com.personalmoney.core.currency.FxPegs
import com.personalmoney.core.currency.FxRate
import com.personalmoney.core.currency.toFxRates
import com.personalmoney.core.data.ExchangeRateRepository as SharedExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The shared rate contract, backed by Room.
 *
 * No [refresh]: fetched rows arrive from `FxRefreshWorker`, and Room's flow re-emits the
 * moment they land, so there is nothing for a caller to pull. Hand-set rates are written
 * straight through here, and that worker leaves their currencies alone.
 */
class RoomExchangeRateRepository(
    private val delegate: ExchangeRateRepository,
) : SharedExchangeRateRepository {

    override fun observeAll(): Flow<List<FxRate>> =
        delegate.observeAll().map { it.toFxRates() }

    override suspend fun handRate(code: String): Double? =
        delegate.find(code.uppercase(), FxPegs.HUB)?.rate

    override suspend fun setHandRate(code: String, perUsd: Double?) {
        val quote = code.uppercase()
        if (perUsd == null || !perUsd.isFinite() || perUsd <= 0.0) {
            delegate.clearManual(quote, FxPegs.HUB)
            return
        }
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        delegate.saveManual(quote, FxPegs.HUB, perUsd, today)
    }
}
