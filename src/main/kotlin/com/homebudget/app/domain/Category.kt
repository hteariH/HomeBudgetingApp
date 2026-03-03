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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: CategoryType,

    @Column(nullable = false)
    var icon: String = "tag",

    @Column(nullable = false)
    var color: String = "#6366F1",

    @Column
    var description: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
