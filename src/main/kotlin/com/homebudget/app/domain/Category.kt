package com.homebudget.app.domain

import jakarta.persistence.*
import java.time.LocalDateTime

enum class CategoryType {
    INCOME, EXPENSE
}

@Entity
@Table(name = "categories")
data class Category(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

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

    @Column
    var description: String? = null,

    /** MCC codes (Merchant Category Codes) learned from Monobank imports */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_mcc_codes", joinColumns = [JoinColumn(name = "category_id")])
    @Column(name = "mcc_code")
    var mccCodes: MutableSet<Int> = mutableSetOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
