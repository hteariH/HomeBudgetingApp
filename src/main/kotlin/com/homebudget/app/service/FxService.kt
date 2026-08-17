package com.homebudget.app.service

import com.homebudget.app.domain.ExchangeRate
import com.homebudget.app.domain.RateSource
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.ExchangeRateRepository
import com.homebudget.app.repository.UserCurrencySettingRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Daily exchange rates from Frankfurter.
 *
 * Deliberately **v2** (`api.frankfurter.dev`), not v1: v1 serves only the ~30 ECB
 * currencies and silently omits UAH and RUB, which are among the ones this app is
 * most used with. v2 covers ~165. The Android client learned this the hard way - see
 * its `core/currency/FrankfurterApi` - so do not "simplify" the host back.
 *
 * Fetching here rather than on each device means one request a day for the whole
 * install, and every client converting with identical numbers.
 */
@Service
class FxService(
    private val currencyRepo: CurrencyRepository,
    private val settingRepo: UserCurrencySettingRepository,
    private val rateRepo: ExchangeRateRepository,
    private val clock: Clock,
    @Value("\${app.fx.base:USD}") private val baseCode: String,
    @Value("\${app.fx.url:https://api.frankfurter.dev/}") private val baseUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http: RestClient by lazy { RestClient.builder().baseUrl(baseUrl).build() }

    private data class FrankfurterRate(
        val date: String? = null,
        val base: String? = null,
        val quote: String? = null,
        val rate: BigDecimal? = null
    )

    /**
     * Refreshes every currency anybody has switched on. Currencies nobody uses are
     * skipped - there is no point pulling 104 pairs a day to convert three.
     */
    @Scheduled(cron = "\${app.fx.cron:0 30 4 * * *}")
    @Transactional
    fun refreshRates(): Int {
        val quotes = settingRepo.activeCurrencyCodes()
            .filter { it != baseCode }
            .distinct()
        if (quotes.isEmpty()) {
            log.debug("No active currencies; skipping FX refresh")
            return 0
        }

        val fetched = try {
            http.get()
                .uri { it.path("v2/rates").queryParam("base", baseCode).queryParam("quotes", quotes.joinToString(",")).build() }
                .retrieve()
                .body(Array<FrankfurterRate>::class.java)
                ?.toList()
                .orEmpty()
        } catch (e: Exception) {
            // A missed refresh is not worth failing anything over; yesterday's rates
            // stay in place and the job runs again tomorrow.
            log.warn("FX refresh failed: {}", e.message)
            return 0
        }

        val today = LocalDate.now(clock)
        var stored = 0
        for (row in fetched) {
            val quote = row.quote ?: continue
            val rate = row.rate ?: continue
            if (rate.signum() <= 0) {
                // A zero or negative rate would turn into a division by zero downstream.
                log.warn("Ignoring non-positive rate for {}/{}", baseCode, quote)
                continue
            }
            val existing = rateRepo.findByBaseCodeAndQuoteCodeAndRateDateAndUserIsNull(baseCode, quote, today)
            if (existing != null) {
                existing.rate = rate
                existing.updatedAt = LocalDateTime.now(clock)
                rateRepo.save(existing)
            } else {
                rateRepo.save(
                    ExchangeRate(
                        baseCode = baseCode,
                        quoteCode = quote,
                        rate = rate,
                        rateDate = today,
                        source = RateSource.FRANKFURTER,
                        updatedAt = LocalDateTime.now(clock)
                    )
                )
            }
            stored++
        }
        log.info("Stored {} exchange rates for {}", stored, today)
        return stored
    }

    /** Seeds a newly registered user's active currencies from the catalog defaults. */
    @Transactional
    fun activeCodesOrDefault(): List<String> =
        settingRepo.activeCurrencyCodes().ifEmpty { listOf(baseCode) }

    fun catalogSize(): Long = currencyRepo.count()
}
