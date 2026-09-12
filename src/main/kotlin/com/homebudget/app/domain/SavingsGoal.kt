package com.homebudget.app.domain

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "savings_goals")
@SQLRestriction("deleted = false")
data class SavingsGoal(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    override val uuid: String = Syncable.newUuid(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "target_amount_minor", nullable = false)
    var targetAmountMinor: Long,

    @Column(name = "currency_code", nullable = false, length = 10)
    var currencyCode: String,

    @Column(name = "current_amount_minor", nullable = false)
    var currentAmountMinor: Long = 0,

    @Column(name = "target_date")
    var targetDate: LocalDate? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_account_id")
    var linkedAccount: Account? = null,

    @Column(nullable = false, length = 50)
    var icon: String = "flag",

    @Column(nullable = false, length = 20)
    var color: String = "#10B981",

    @Column(nullable = false)
    var archived: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now(),

    @Column(nullable = false)
    override var deleted: Boolean = false
) : Syncable
