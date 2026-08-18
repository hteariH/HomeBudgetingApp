package com.personalmoney.core.currency

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.domain.ExchangeRateSource
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

class FxRefreshWorker constructor(
    context: Context,
    params: WorkerParameters,
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

        val response = api.rates(base = base, quotes = symbols.joinToString(","))
        val now = System.currentTimeMillis()
        val entries = response.map { r ->
            ExchangeRateEntity(
                baseCode = r.base,
                quoteCode = r.quote,
                rate = r.rate,
                date = LocalDate.parse(r.date),
                source = ExchangeRateSource.FRANKFURTER,
                updatedAtEpochMs = now
            )
        }
        if (entries.isNotEmpty()) exchangeRates.upsertAll(entries)
        userPreferences.setFxLastRefresh(now)
        Result.success()
    }.getOrElse {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "fx_refresh"
        const val ONE_TIME_NAME = "fx_refresh_once"
    }
}
