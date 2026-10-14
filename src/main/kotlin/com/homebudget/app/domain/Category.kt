package com.homebudget.app.domain

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.time.LocalDateTime

enum class CategoryType {
    INCOME, EXPENSE
}

@Entity
@Table(name = "categories")
@SQLRestriction("deleted = false")
data class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36, updatable = false)
    override val uuid: String = Syncable.newUuid(),

    @Column(nullable = false)
    var name: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: CategoryType,

    @Column(nullable = false)
    var icon: String = "tag",

    @Column(nullable = false)
    var color: String = "#6366F1",

    @Column(length = 500)
    var description: String? = null,

    /** Sub-categories. Self-referencing, one level deep in practice. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Category? = null,

    @Column(nullable = false)
    var archived: Boolean = false,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,

    /**
     * Optional monthly spending limit in the user's base currency, minor units.
     * Expense categories only.
     */
    @Column(name = "budget_limit_minor")
    var budgetLimitMinor: Long? = null,

    /**
     * The id this category had in whatever it was imported from, namespaced by source
     * (`homemoney:42`). It is what makes a second import adopt the rows the first one
     * created instead of building the tree again.
     */
    @Column(name = "external_id", length = 100)
    var externalId: String? = null,

    /** MCC codes (Merchant Category Codes) learned from Monobank imports */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_mcc_codes", joinColumns = [JoinColumn(name = "category_id")])
    @Column(name = "mcc_code")
    var mccCodes: MutableSet<Int> = mutableSetOf(),

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now(),

    @Column(nullable = false)
    override var deleted: Boolean = false
) : Syncable
