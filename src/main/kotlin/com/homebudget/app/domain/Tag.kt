package com.homebudget.app.domain

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDateTime

@Entity
@Table(name = "tags")
@SQLRestriction("deleted = false")
data class Tag(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    override val uuid: String = Syncable.newUuid(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false, length = 50)
    var name: String,

    @Column(nullable = false, length = 20)
    var color: String = "#6366F1",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now(),

    @Column(nullable = false)
    override var deleted: Boolean = false
) : Syncable

@Entity
@Table(name = "transaction_tags")
@SQLRestriction("deleted = false")
data class TransactionTag(
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
    @JoinColumn(name = "tag_id", nullable = false)
    var tag: Tag,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now(),

    @Column(nullable = false)
    override var deleted: Boolean = false
) : Syncable
