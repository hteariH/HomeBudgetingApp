package com.homebudget.app.repository

import com.homebudget.app.domain.PlayPurchase
import com.homebudget.app.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PlayPurchaseRepository : JpaRepository<PlayPurchase, Long> {

    fun findByTokenHash(tokenHash: String): PlayPurchase?

    fun findAllByUser(user: User): List<PlayPurchase>

    /**
     * Purchases worth asking Google about again.
     *
     * A subscription that has not expired yet needs no call - nothing can have changed
     * that matters before its expiry. One that has just run out either renewed (and the
     * new expiry is what keeps the user subscribed) or did not, and either way the answer
     * is only knowable from Google. The window reaches a little past the expiry so a
     * renewal that Google records slightly late is still picked up.
     */
    @Query(
        """
        SELECT p FROM PlayPurchase p
        WHERE p.expiresAt IS NOT NULL
          AND p.expiresAt <= :until
          AND p.checkedAt < :staleBefore
        ORDER BY p.expiresAt ASC
        """
    )
    fun findWorthRechecking(
        @Param("until") until: Instant,
        @Param("staleBefore") staleBefore: Instant
    ): List<PlayPurchase>
}
