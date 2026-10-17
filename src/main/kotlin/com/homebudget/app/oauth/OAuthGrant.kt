package com.homebudget.app.oauth

import com.homebudget.app.domain.User
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * One consent: a user let a client act for them. Its access and refresh tokens live in
 * `access_tokens` with `oauth_grant_id`; setting [revokedAt] stops all of them at once.
 * Settings lists live grants, identified by [uuid].
 */
@Entity
@Table(name = "oauth_grants")
class OAuthGrant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 36)
    val uuid: String = java.util.UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    val client: OAuthClient,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 100)
    val scope: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
)

interface OAuthGrantRepository : JpaRepository<OAuthGrant, Long> {
    fun findByUuidAndUserIdAndRevokedAtIsNull(uuid: String, userId: Long): OAuthGrant?

    /** The user's live grants that obtained tokens; an approval never exchanged is not a connection. */
    @Query(
        "SELECT g FROM OAuthGrant g WHERE g.user.id = :userId AND g.revokedAt IS NULL " +
            "AND EXISTS (SELECT t.id FROM AccessToken t WHERE t.oauthGrantId = g.id)"
    )
    fun findConnectedByUser(@Param("userId") userId: Long): List<OAuthGrant>
}
