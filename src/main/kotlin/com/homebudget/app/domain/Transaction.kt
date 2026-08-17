package com.homebudget.app.domain

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

enum class TransactionType {
    INCOME, EXPENSE, TRANSFER
}

/**
 * A single ledger row.
 *
 * A transfer is *two* of these sharing a [transferGroupId]: a negative debit on the
 * source account and a positive credit on the destination. One entity type keeps the
 * sync protocol and the balance query simple, and cross-currency transfers fall out
 * for free because each row carries its own [accountAmountMinor].
 *
 * Sign convention, mirrored from the Android client's balance query:
 * - INCOME: [accountAmountMinor] is a positive magnitude, added
 * - EXPENSE: [accountAmountMinor] is a positive magnitude, subtracted
 * - TRANSFER: [accountAmountMinor] is already signed, added as-is
 */
@Entity
@Table(name = "transactions")
@SQLRestriction("deleted = false")
data class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    override val uuid: String = Syncable.newUuid(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    var account: Account,

    /** Null for transfer rows, and for history whose category was deleted. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    /** Amount as entered, in [currencyCode], in minor units. */
    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long,

    @Column(name = "currency_code", nullable = false, length = 10)
    var currencyCode: String,

    /** The same amount in the account's currency; equal to [amountMinor] unless cross-currency. */
    @Column(name = "account_amount_minor", nullable = false)
    var accountAmountMinor: Long,

    @Column(name = "fx_rate_to_account", nullable = false, precision = 24, scale = 10)
    var fxRateToAccount: BigDecimal = BigDecimal.ONE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: TransactionType,

    /** An instant rather than a date: same-day entries are ordered by time. */
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant,

    @Column
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    var subscription: Subscription? = null,

    @Column(name = "transfer_group_id", length = 36)
    var transferGroupId: String? = null,

    @Column(name = "transfer_counterpart_account_id")
    var transferCounterpartAccountId: Long? = null,

    @Column(name = "transfer_fee_minor")
    var transferFeeMinor: Long? = null,

    /** The bank's own id for this operation, so an import cannot bring it in twice. */
    @Column(name = "external_id", length = 100)
    var externalId: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now(),

    @Column(nullable = false)
    override var deleted: Boolean = false
) : Syncable
