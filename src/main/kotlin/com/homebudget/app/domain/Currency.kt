package com.homebudget.app.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "currencies")
data class Currency(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // 10, not 3: the catalog carries 4-character tickers (USDT, USDC).
    @Column(nullable = false, unique = true, length = 10)
    val code: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, length = 10)
    val symbol: String,

    /**
     * Digits after the decimal point for this currency, and the exponent that turns a
     * minor-unit amount into a displayed one. Not every currency is two-decimal:
     * JPY and KRW are 0, the Gulf dinars are 3, BTC is 8.
     */
    @Column(nullable = false)
    val decimals: Int = 2,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,

    /**
     * Legacy single-rate FX, kept only until the React client moves onto the
     * `exchange_rates` table. Do not build anything new on these two columns.
     */
    @Column(nullable = false, precision = 18, scale = 6)
    val exchangeRateToBase: BigDecimal = BigDecimal.ONE, // rate relative to USD

    @Column(nullable = false)
    val isBase: Boolean = false,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
