package com.homebudget.app.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class RateSource {
    MANUAL, FRANKFURTER
}

/**
 * One currency pair on one day.
 *
 * [household] is null for rates fetched globally from Frankfurter, and set when a
 * household has overridden that pair by hand. Fetching centrally rather than from each
 * device means one request per day for the whole install instead of one per phone, and
 * every client converting with the same numbers.
 */
@Entity
@Table(name = "exchange_rates")
data class ExchangeRate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "base_code", nullable = false, length = 10)
    var baseCode: String,

    @Column(name = "quote_code", nullable = false, length = 10)
    var quoteCode: String,

    @Column(nullable = false, precision = 24, scale = 10)
    var rate: BigDecimal,

    @Column(name = "rate_date", nullable = false)
    var rateDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var source: RateSource = RateSource.FRANKFURTER,

    /** Null for a fetched rate; a household's own override otherwise. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    var household: Household? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
