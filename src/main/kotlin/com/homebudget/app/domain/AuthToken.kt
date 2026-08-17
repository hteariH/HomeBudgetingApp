package com.homebudget.app.domain

import jakarta.persistence.*
import java.time.Instant

enum class AuthTokenPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}

/**
 * A single-use, expiring link token.
 *
 * Both flows share this type because they are the same object with a different label:
 * a random secret mailed to an address, good once, for a while.
 *
 * Only the hash is stored. A password-reset link is a full account takeover for as long
 * as it lives, so the table must not contain anything usable if it leaks.
 */
@Entity
@Table(name = "auth_tokens")
data class AuthToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var purpose: AuthTokenPurpose,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    /** Set on redemption; a second use of the same link must fail. */
    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
