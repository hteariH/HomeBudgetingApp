package com.homebudget.app.controller

import com.homebudget.app.repository.ExchangeRateRepository
import com.homebudget.app.repository.preferringOverrides
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.FxService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
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

/** "1 <code> is this many USD", as typed by the user. */
data class FxOverrideRequest(val rate: BigDecimal)

/**
 * Rates for the clients to convert with. The browser reads this instead of calling
 * Frankfurter itself, so both clients agree and the page holds no third-party origin.
 * The Android client refreshes its own copy in a WorkManager job against the same source.
 */
@RestController
@RequestMapping("/api/fx")
class FxController(
    private val rateRepo: ExchangeRateRepository,
    private val fxService: FxService,
    private val securityUtils: SecurityUtils
) {

    @GetMapping("/rates")
    fun rates(): List<FxRateResponse> =
        rateRepo.latestFor(securityUtils.getCurrentUser()).preferringOverrides().map {
            FxRateResponse(
                baseCode = it.baseCode,
                quoteCode = it.quoteCode,
                rate = it.rate,
                rateDate = it.rateDate,
                source = it.source.name
            )
        }

    /**
     * Sets this user's own rate for a currency, for the ones no feed carries - bitcoin,
     * a voucher currency somebody invented on the currencies screen. Quoted against USD
     * because that is what every triangulated conversion hops through.
     */
    @PutMapping("/rates/{code}")
    fun setOverride(@PathVariable code: String, @RequestBody body: FxOverrideRequest): ResponseEntity<Void> {
        fxService.setOverride(securityUtils.getCurrentUser(), code, body.rate)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/rates/{code}")
    fun clearOverride(@PathVariable code: String): ResponseEntity<Void> {
        fxService.clearOverride(securityUtils.getCurrentUser(), code)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/refresh")
    fun refresh(): ResponseEntity<Map<String, Any>> {
        val count = fxService.refreshRates()
        return ResponseEntity.ok(mapOf("refreshed" to count))
    }
}
