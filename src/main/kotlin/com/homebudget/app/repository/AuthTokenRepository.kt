package com.homebudget.app.repository

import com.homebudget.app.domain.AuthToken
import com.homebudget.app.domain.AuthTokenPurpose
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface AuthTokenRepository : JpaRepository<AuthToken, Long> {
    fun findByTokenHash(tokenHash: String): AuthToken?

    /** Issuing a new link retires the previous ones, so only the newest works. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AuthToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    fun invalidateOutstanding(userId: Long, purpose: AuthTokenPurpose, now: Instant): Int
}
