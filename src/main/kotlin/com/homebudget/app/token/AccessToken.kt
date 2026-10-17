package com.homebudget.app.token

import com.homebudget.app.domain.User
import jakarta.persistence.*
import java.time.Instant

enum class AccessTokenKind { PERSONAL, OAUTH_ACCESS, OAUTH_REFRESH }

/**
 * A credential an assistant presents on `/mcp` - a personal token made in Settings, or the
 * access and refresh tokens an OAuth grant issues.
 *
 * Only the SHA-256 of the token is kept, so revocation is a row delete and a database leak
 * hands out no working tokens. [prefix] is the token's first characters, shown in lists so
 * a person can tell their tokens apart.
 */
@Entity
@Table(name = "access_tokens")
class AccessToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val kind: AccessTokenKind,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(nullable = false, length = 10)
    val prefix: String,

    /** The OAuth grant that issued it; null for a personal token. */
    @Column(name = "oauth_grant_id")
    val oauthGrantId: Long? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    /** Refresh tokens: when it was rotated. A used token never works again. */
    @Column(name = "used_at")
    var usedAt: Instant? = null
)
