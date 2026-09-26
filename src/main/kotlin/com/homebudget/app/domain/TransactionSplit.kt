package com.homebudget.app.domain

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDateTime

@Entity
@Table(name = "transaction_splits")
@SQLRestriction("deleted = false")
data class TransactionSplit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    override val uuid: String = Syncable.newUuid(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    var transaction: Transaction,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long,

    @Column
    var note: String? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now(),

    @Column(nullable = false)
    override var deleted: Boolean = false
) : Syncable
