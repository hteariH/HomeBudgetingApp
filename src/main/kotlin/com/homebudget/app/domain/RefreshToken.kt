package com.homebudget.app.domain

import jakarta.persistence.*
import java.time.Instant

/**
 * A long-lived credential that buys a new short-lived access token.
 *
 * Only the SHA-256 of the token is stored: the table is a list of live sessions, and
 * a database leak should not hand an attacker working sessions the way storing the raw
 * value would.
 */
@Entity
@Table(name = "refresh_tokens")
data class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
