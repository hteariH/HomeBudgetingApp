package com.homebudget.app.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

enum class AccountType {
    CHECKING, SAVINGS, CASH, CREDIT, INVESTMENT
}

@Entity
@Table(name = "accounts")
data class Account(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: AccountType = AccountType.CHECKING,

    @Column(nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    var currency: Currency,

    @Column
    var description: String? = null,

    @Column(nullable = false)
    var color: String = "#4F46E5",

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
