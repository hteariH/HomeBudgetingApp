package com.homebudget.app.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "transfers")
data class Transfer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "from_account_id", nullable = false)
    var fromAccount: Account,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "to_account_id", nullable = false)
    var toAccount: Account,

    @Column(nullable = false, precision = 18, scale = 2)
    var fromAmount: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    var toAmount: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 6)
    var exchangeRate: BigDecimal = BigDecimal.ONE,

    @Column(nullable = false)
    var date: LocalDate,

    @Column
    var description: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
