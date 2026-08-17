package com.homebudget.app.domain

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDateTime

enum class AccountType {
    CHECKING, SAVINGS, CASH, CREDIT, INVESTMENT
}

// Tombstoned rows are invisible to every JPQL query, including the balance sum.
// The sync endpoints read them back with native SQL, which @SQLRestriction does not touch.
@Entity
@Table(name = "accounts")
@SQLRestriction("deleted = false")
data class Account(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    override val uuid: String = Syncable.newUuid(),

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: AccountType = AccountType.CHECKING,

    /**
     * Opening balance in minor units. This is *not* the current balance - that is
     * summed from the ledger by [com.homebudget.app.repository.AccountRepository.balancesOf].
     * Storing a running total meant every write had to mutate it, and a replayed
     * write applied the delta twice.
     */
    @Column(name = "initial_balance_minor", nullable = false)
    var initialBalanceMinor: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "currency_id", nullable = false)
    var currency: Currency,

    @Column
    var description: String? = null,

    @Column(nullable = false)
    var icon: String = "wallet",

    @Column(nullable = false)
    var color: String = "#4F46E5",

    @Column(nullable = false)
    var archived: Boolean = false,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    /** Keeps an account on the books but out of the net-worth figure. */
    @Column(name = "include_in_total", nullable = false)
    var includeInTotal: Boolean = true,

    /**
     * Which bank connection feeds this account. Credentials live on the connection,
     * not here: one set usually covers several accounts.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_connection_id")
    var bankConnection: BankConnection? = null,

    /** The bank's own id for this account. */
    @Column(name = "external_account_id", length = 100)
    var externalAccountId: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now(),

    @Column(nullable = false)
    override var deleted: Boolean = false
) : Syncable
