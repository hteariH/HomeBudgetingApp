package com.homebudget.app.household

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface HouseholdInviteRepository : JpaRepository<HouseholdInvite, Long> {
    fun findByTokenHash(tokenHash: String): HouseholdInvite?

    @Query(
        """
        SELECT i FROM HouseholdInvite i
        WHERE i.household.id = :householdId AND i.usedAt IS NULL AND i.revokedAt IS NULL
          AND i.expiresAt > :now
        ORDER BY i.createdAt
        """
    )
    fun findLive(householdId: Long, now: Instant): List<HouseholdInvite>

    @Query(
        """
        SELECT COUNT(i) FROM HouseholdInvite i
        WHERE i.household.id = :householdId AND i.usedAt IS NULL AND i.revokedAt IS NULL
          AND i.expiresAt > :now
        """
    )
    fun countLive(householdId: Long, now: Instant): Long
}
