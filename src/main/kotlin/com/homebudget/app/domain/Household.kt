package com.homebudget.app.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Whose ledger it is. Accounts, categories, subscriptions, transactions, currency
 * settings, manual rates and bank connections all belong to a household, and every login
 * in it sees and edits the same books.
 */
@Entity
@Table(name = "households")
data class Household(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    val uuid: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    var name: String,

    /** The currency totals are reported in. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "base_currency_id")
    var baseCurrency: Currency? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
