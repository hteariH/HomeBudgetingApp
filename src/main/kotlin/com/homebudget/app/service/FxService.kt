package com.homebudget.app.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.homebudget.app.domain.ExchangeRate
import com.homebudget.app.domain.RateSource
import com.homebudget.app.domain.User
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.ExchangeRateRepository
import com.homebudget.app.repository.preferringOverrides
import com.homebudget.app.repository.UserCurrencySettingRepository
import com.homebudget.app.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.math.RoundingMode
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
    private val accountRepo: AccountRepository,
    private val userRepo: UserRepository,
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

    private data class FrankfurterCurrency(
        @JsonProperty("iso_code") val isoCode: String? = null
    )

    /**
     * Refreshes every currency somebody actually uses. Currencies nobody holds are
     * skipped - there is no point pulling 104 pairs a day to convert three.
     */
    @Scheduled(cron = "\${app.fx.cron:0 30 4 * * *}")
    @Transactional
    fun refreshRates(): Int {
        val wanted = quotedCodes()
        // Frankfurter answers one unknown code with 422 for the *whole* request, so a
        // single account in a currency it has never heard of - a stablecoin, a voucher
        // currency somebody invented on the currencies screen - used to leave the entire
        // install on the rates it happened to have that day, quietly, for good. Ask it
        // what it knows first and send only that.
        val supported = supportedQuotes()
        val quotes = if (supported == null) wanted else wanted.filter { it in supported }
        if (quotes.isEmpty()) {
            log.debug("No quotable currencies; skipping FX refresh")
            return 0
        }
        (wanted - quotes.toSet()).takeIf { it.isNotEmpty() }?.let {
            log.info("Not quoting {} - Frankfurter does not carry them", it)
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

    /**
     * The rate to convert [from] into [to] with, or null when no stored pair gets there.
     *
     * Mirrors the clients' `FxSnapshot`: a stored pair first, then its inverse, then a
     * hop through [baseCode]. Kept here so a server-side conversion - importing a row as
     * a cross-currency transfer, say - uses the same numbers the phone would show.
     */
    @Transactional(readOnly = true)
    fun rate(user: User, from: String, to: String): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val stored = rateRepo.latestFor(user).preferringOverrides()
            .filter { it.rate.signum() > 0 }
            .associate { (it.baseCode to it.quoteCode) to it.rate }
        val byPair = stored + pegsMissingFrom(stored)

        fun direct(base: String, quote: String) = byPair[base to quote]
        fun inverse(base: String, quote: String) =
            byPair[quote to base]?.let { BigDecimal.ONE.divide(it, RATE_SCALE, RoundingMode.HALF_UP) }
        fun either(base: String, quote: String) = direct(base, quote) ?: inverse(base, quote)

        either(from, to)?.let { return it }
        // Two pivots, because the two are only the same currency by default: rates are
        // quoted against [baseCode], while a peg is defined against [FxPegs.HUB], and an
        // install configured to quote against anything else would put every pegged
        // currency out of reach of the only hop that could have found it.
        for (pivot in listOf(baseCode, FxPegs.HUB).distinct()) {
            val fromPivot = either(from, pivot) ?: continue
            val pivotTo = either(pivot, to) ?: continue
            return fromPivot.multiply(pivotTo).setScale(RATE_SCALE, RoundingMode.HALF_UP)
        }
        return null
    }

    /**
     * Which currencies to quote.
     *
     * The accounts and the users' base currencies are in here, not just the switched-on
     * settings, because `user_currency_settings` is written by the phone's currency screen
     * alone: a browser-only user never creates a row there, so quoting settings by
     * themselves left them with no rates at all and every cross-currency transfer silently
     * converting one-to-one.
     */
    fun quotedCodes(): List<String> =
        (settingRepo.activeCurrencyCodes() + accountRepo.currencyCodesInUse() + userRepo.baseCurrencyCodes())
            .filter { it != baseCode }
            // A pegged currency's rate is a definition, not something to go and ask about.
            .filterNot { FxPegs.isPegged(it) }
            .distinct()

    /**
     * The codes Frankfurter will accept, or null when it would not say.
     *
     * One request a day, and it buys the difference between a partial answer and none at
     * all - see [refreshRates]. Null means "could not tell", and the caller then sends
     * the unfiltered list rather than skipping a refresh nobody asked it to skip.
     */
    private fun supportedQuotes(): Set<String>? = try {
        http.get()
            .uri { it.path("v2/currencies").build() }
            .retrieve()
            .body(Array<FrankfurterCurrency>::class.java)
            ?.mapNotNullTo(mutableSetOf()) { it.isoCode?.uppercase() }
            ?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        log.warn("Could not read Frankfurter's currency list: {}", e.message)
        null
    }

    /** [FxPegs] for the currencies the stored rates say nothing about. See the client's `FxSnapshot`. */
    private fun pegsMissingFrom(stored: Map<Pair<String, String>, BigDecimal>): Map<Pair<String, String>, BigDecimal> {
        val quoted = stored.keys.flatMapTo(mutableSetOf()) { listOf(it.first, it.second) }
        return FxPegs.toHub
            .filterKeys { it !in quoted }
            .mapKeys { (code, _) -> code to FxPegs.HUB }
    }

    // ── Rates set by hand ───────────────────────────────────────────────────

    /**
     * A rate the user typed in, for a currency no feed carries.
     *
     * Always against [FxPegs.HUB], because that is the currency [rate] hops through: an
     * override stored against anything else converts to that one thing and nothing else,
     * which is not what somebody who owns bitcoin and thinks in hryvnia is asking for.
     *
     * One row per user and pair, re-dated on every save. Keeping a history would make the
     * nightly job's fresher global rate win, which is exactly what an override is for.
     */
    @Transactional
    fun setOverride(user: User, code: String, perHub: BigDecimal) {
        val quote = code.uppercase()
        require(perHub.signum() > 0) { "A rate has to be greater than zero" }
        require(quote != FxPegs.HUB) { "${FxPegs.HUB} is what rates are quoted against" }
        val existing = rateRepo.findByUserAndBaseCodeAndQuoteCode(user, quote, FxPegs.HUB)
        // More than one only if an older build wrote a history; collapse it while here.
        existing.drop(1).forEach { rateRepo.delete(it) }
        val row = existing.firstOrNull() ?: ExchangeRate(
            baseCode = quote,
            quoteCode = FxPegs.HUB,
            rate = perHub,
            rateDate = LocalDate.now(clock),
            source = RateSource.MANUAL,
            user = user
        )
        row.rate = perHub
        row.rateDate = LocalDate.now(clock)
        row.source = RateSource.MANUAL
        row.updatedAt = LocalDateTime.now(clock)
        rateRepo.save(row)
    }

    /** Drops the override, putting the currency back on whatever the feed says - often nothing. */
    @Transactional
    fun clearOverride(user: User, code: String) {
        rateRepo.deleteAll(rateRepo.findByUserAndBaseCodeAndQuoteCode(user, code.uppercase(), FxPegs.HUB))
    }

    /** Seeds a newly registered user's active currencies from the catalog defaults. */
    @Transactional
    fun activeCodesOrDefault(): List<String> =
        settingRepo.activeCurrencyCodes().ifEmpty { listOf(baseCode) }

    fun catalogSize(): Long = currencyRepo.count()

    private companion object {
        /** Enough digits that an inverted or triangulated rate keeps its precision. */
        const val RATE_SCALE = 12
    }
}
