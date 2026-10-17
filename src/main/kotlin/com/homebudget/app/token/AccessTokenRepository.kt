package com.homebudget.app.token

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccessTokenRepository : JpaRepository<AccessToken, Long> {
    fun findByTokenHash(tokenHash: String): AccessToken?

    fun findAllByUserIdAndKindOrderByCreatedAtDescIdDesc(userId: Long, kind: AccessTokenKind): List<AccessToken>

    fun findByIdAndUserIdAndKind(id: Long, userId: Long, kind: AccessTokenKind): AccessToken?

    /** Whether the OAuth grant behind a token still stands. */
    @Query("SELECT COUNT(g) FROM OAuthGrant g WHERE g.id = :id AND g.revokedAt IS NULL")
    fun countLiveGrants(@Param("id") grantId: Long): Long

    @Query("SELECT t.id FROM AccessToken t WHERE t.oauthGrantId = :grantId")
    fun findIdsByGrant(@Param("grantId") grantId: Long): List<Long>

    @Query("SELECT MAX(t.lastUsedAt) FROM AccessToken t WHERE t.oauthGrantId = :grantId")
    fun lastUsedByGrant(@Param("grantId") grantId: Long): java.time.Instant?

    /**
     * OAuth tokens that expired before [cutoff], and every token of a grant revoked before it.
     * A rotated refresh token stays until it expires: presenting it again must still revoke.
     */
    @Modifying
    @Query(
        "DELETE FROM AccessToken t WHERE t.oauthGrantId IS NOT NULL AND (t.expiresAt < :cutoff " +
            "OR t.oauthGrantId IN (SELECT g.id FROM OAuthGrant g WHERE g.revokedAt < :cutoff))"
    )
    fun deleteStaleOAuth(@Param("cutoff") cutoff: java.time.Instant): Int

    /** Marks a refresh token rotated unless it already was; 0 means someone got there first. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE AccessToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    fun markUsed(@Param("id") id: Long, @Param("now") now: java.time.Instant): Int
}
