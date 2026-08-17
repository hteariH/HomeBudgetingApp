package com.homebudget.app.controller

import com.homebudget.app.repository.ExchangeRateRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

data class FxRateResponse(
    val baseCode: String,
    val quoteCode: String,
    val rate: BigDecimal,
    val rateDate: LocalDate,
    val source: String
)

/**
 * Rates for the clients to convert with. The Android client reads this instead of
 * calling Frankfurter itself, so both clients agree and neither has to hold an API
 * dependency of its own.
 */
@RestController
@RequestMapping("/api/fx")
class FxController(
    private val rateRepo: ExchangeRateRepository,
    private val securityUtils: SecurityUtils
) {

    @GetMapping("/rates")
    fun rates(): List<FxRateResponse> =
        rateRepo.latestFor(securityUtils.getCurrentUser()).map {
            FxRateResponse(
                baseCode = it.baseCode,
                quoteCode = it.quoteCode,
                rate = it.rate,
                rateDate = it.rateDate,
                source = it.source.name
            )
        }
}
