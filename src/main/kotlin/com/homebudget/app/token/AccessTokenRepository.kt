package com.homebudget.app.token

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccessTokenRepository : JpaRepository<AccessToken, Long> {
    fun findByTokenHash(tokenHash: String): AccessToken?

    fun findAllByUserIdAndKindOrderByCreatedAtDescIdDesc(userId: Long, kind: AccessTokenKind): List<AccessToken>

    fun findByIdAndUserIdAndKind(id: Long, userId: Long, kind: AccessTokenKind): AccessToken?

    /** Whether the OAuth grant behind a token still stands. Native: the grant has no entity yet. */
    @Query(value = "SELECT COUNT(*) FROM oauth_grants WHERE id = :id AND revoked_at IS NULL", nativeQuery = true)
    fun countLiveGrants(@Param("id") grantId: Long): Long
}
