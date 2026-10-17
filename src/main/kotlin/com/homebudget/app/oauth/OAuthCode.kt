package com.homebudget.app.oauth

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * An authorization code: single use, short-lived, bound to its grant (and so the client and
 * user), the redirect URI and the PKCE challenge. Only its SHA-256 is kept.
 */
@Entity
@Table(name = "oauth_codes")
class OAuthCode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    val codeHash: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "grant_id", nullable = false)
    val grant: OAuthGrant,

    @Column(name = "redirect_uri", nullable = false, length = 1000)
    val redirectUri: String,

    @Column(name = "code_challenge", nullable = false, length = 128)
    val codeChallenge: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null
)

interface OAuthCodeRepository : JpaRepository<OAuthCode, Long> {
    fun findByCodeHash(codeHash: String): OAuthCode?

    /** Marks the code used unless it already was; 0 means someone got there first. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE OAuthCode c SET c.usedAt = :now WHERE c.id = :id AND c.usedAt IS NULL")
    fun markUsed(@Param("id") id: Long, @Param("now") now: Instant): Int

    @Modifying
    @Query("DELETE FROM OAuthCode c WHERE c.expiresAt < :cutoff")
    fun deleteExpiredBefore(@Param("cutoff") cutoff: Instant): Int
}
