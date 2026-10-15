package com.homebudget.app.domain

import jakarta.persistence.*
import java.time.Instant

enum class HouseholdRole { OWNER, MEMBER }

/** A login's place in a household. One per user for now; invitations add more. */
@Entity
@Table(name = "household_members")
data class HouseholdMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "household_id", nullable = false)
    val household: Household,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: HouseholdRole,

    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: Instant = Instant.now()
)
