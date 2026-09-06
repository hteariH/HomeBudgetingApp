package com.personalmoney.core.currency

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.data.repository.CurrencyRepository
import com.personalmoney.core.data.repository.ExchangeRateRepository
import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.domain.ExchangeRateSource
import com.personalmoney.core.util.AppLog
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
        // A rate somebody typed in is an answer, not a gap waiting to be filled.
        val handSet = exchangeRates.observeAll().first()
            .filter { it.source == ExchangeRateSource.MANUAL }
            .mapTo(mutableSetOf()) { it.baseCode }
        val wanted = active.map { it.code }
            .filter { it != base }
            .filterNot { FxPegs.isPegged(it) }
            .filterNot { it in handSet }
        if (wanted.isEmpty()) return Result.success()

        val symbols = quotable(wanted)
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

    /**
     * [wanted], minus whatever Frankfurter has never heard of.
     *
     * One unknown code makes it answer 422 for the *whole* request, so a single account in
     * a currency it does not carry - a stablecoin, a voucher currency invented on the
     * currencies screen - left the device converting with whatever rates it happened to
     * have that day, silently, for good. `Result.retry()` then re-ran the same doomed
     * request forever.
     *
     * When the list itself cannot be read, the unfiltered set is sent: that is the old
     * behaviour, it still works for the fiat-only phones that are most of them, and the
     * next run tries again.
     */
    private suspend fun quotable(wanted: List<String>): List<String> {
        val supported = runCatching { api.currencies() }
            .onFailure { AppLog.w(TAG, "Could not read Frankfurter's currency list: ${it.message}") }
            .getOrNull()
            ?.mapTo(mutableSetOf()) { it.isoCode.uppercase() }
            ?.takeIf { it.isNotEmpty() }
            ?: return wanted
        val quotable = wanted.filter { it.uppercase() in supported }
        (wanted - quotable.toSet()).takeIf { it.isNotEmpty() }
            ?.let { AppLog.d(TAG, "Not quoting $it - Frankfurter does not carry them") }
        return quotable
    }

    companion object {
        private const val TAG = "FxRefreshWorker"
        const val UNIQUE_NAME = "fx_refresh"
        const val ONE_TIME_NAME = "fx_refresh_once"
    }
}
