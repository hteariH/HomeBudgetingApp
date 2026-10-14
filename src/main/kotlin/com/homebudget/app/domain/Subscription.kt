package com.homebudget.app.domain

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

enum class SubscriptionFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

/**
 * A recurring charge. [SubscriptionService] walks [nextChargeDate] forward on a
 * schedule and writes one transaction per charge that has come due, stamping it with
 * the subscription id.
 *
 * Generation lives on the server rather than in each client so that a phone that has
 * not been opened for a month does not hold back the charges, and so that both
 * clients see the same rows.
 */
@Entity
@Table(name = "subscriptions")
@SQLRestriction("deleted = false")
data class Subscription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    override val uuid: String = Syncable.newUuid(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false)
    var name: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    var account: Account,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long,

    @Column(name = "currency_code", nullable = false, length = 10)
    var currencyCode: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var frequency: SubscriptionFrequency,

    /** Every N periods, so "every 3 months" is MONTHLY with an interval of 3. */
    @Column(name = "interval_count", nullable = false)
    var intervalCount: Int = 1,

    @Column(name = "next_charge_date", nullable = false)
    var nextChargeDate: LocalDate,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "reminder_minutes_before")
    var reminderMinutesBefore: Int? = 24 * 60,

    @Column(nullable = false)
    var icon: String = "subscriptions",

    @Column(nullable = false)
    var color: String = "#008080",

    @Column(length = 500)
    var note: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now(),

    @Column(nullable = false)
    override var deleted: Boolean = false
) : Syncable
