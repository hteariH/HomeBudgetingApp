package com.homebudget.app.household

import com.homebudget.app.domain.Household
import com.homebudget.app.domain.User
import jakarta.persistence.*
import java.time.Instant

/** A single-use way into a household, handed on by its owner. */
@Entity
@Table(name = "household_invites")
data class HouseholdInvite(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "household_id", nullable = false)
    val household: Household,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    var createdBy: User? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_user_id")
    var usedBy: User? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
) {
    fun isLive(now: Instant): Boolean = usedAt == null && revokedAt == null && expiresAt.isAfter(now)
}
