package com.personalmoney.core.currency

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.domain.ExchangeRateSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

@HiltWorker
class FxRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: FrankfurterApi,
    private val exchangeRates: ExchangeRateRepository,
    private val currencies: CurrencyRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val base = userPreferences.baseCurrency.first()
        val active = currencies.observeActive().first()
        val symbols = active.map { it.code }.filter { it != base }
        if (symbols.isEmpty()) return Result.success()

        val response = api.latest(base = base, symbols = symbols.joinToString(","))
        val date = LocalDate.parse(response.date)
        val now = System.currentTimeMillis()
        val entries = response.rates.map { (quote, rate) ->
            ExchangeRateEntity(
                baseCode = response.base,
                quoteCode = quote,
                rate = rate,
                date = date,
                source = ExchangeRateSource.FRANKFURTER,
                updatedAtEpochMs = now
            )
        }
        exchangeRates.upsertAll(entries)
        userPreferences.setFxLastRefresh(now)
        Result.success()
    }.getOrElse {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "fx_refresh"
    }
}
