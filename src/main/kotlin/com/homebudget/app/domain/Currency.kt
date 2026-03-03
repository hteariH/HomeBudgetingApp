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

    @Column(nullable = false, unique = true, length = 3)
    val code: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, length = 10)
    val symbol: String,

    @Column(nullable = false, precision = 18, scale = 6)
    val exchangeRateToBase: BigDecimal = BigDecimal.ONE, // rate relative to USD

    @Column(nullable = false)
    val isBase: Boolean = false,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
