package com.homebudget.app.billing

import com.homebudget.app.domain.Entitlement
import com.homebudget.app.domain.EntitlementSource
import com.homebudget.app.domain.User
import com.homebudget.app.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * The only thing that writes [User.entitlement].
 *
 * Everything that can make somebody a subscriber - a verified Play purchase, the nightly
 * re-check, an administrator handing one out - goes through here, so there is one place
 * to read to know how an account became premium and one place that logs it. Enforcement
 * stays where it is: `User.isPremium()` resolves what this writes.
 */
@Service
@Transactional
class EntitlementService(
    private val userRepository: UserRepository,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Makes [user] a subscriber until [until], or indefinitely when it is null.
     *
     * Never shortens an entitlement that already runs longer, whatever granted it. Two
     * sources can cover the same account - a Play subscription and a grant made by hand -
     * and the later expiry is the one the user was promised. Letting a renewal poll that
     * returns an earlier date win would cut short a lifetime grant.
     */
    fun grant(user: User, until: Instant?, source: EntitlementSource, note: String = ""): User {
        if (outlastsGrant(user, until)) {
            log.info(
                "Kept user {}'s longer entitlement from {} ({} offered {})",
                user.id, user.entitlementSource, source, until ?: "forever"
            )
            return user
        }
        user.entitlement = Entitlement.PREMIUM
        user.entitlementExpiresAt = until
        user.entitlementSource = source
        log.info("Granted premium to user {} until {} ({} {})", user.id, until ?: "forever", source, note)
        return userRepository.save(user)
    }

    /**
     * Ends the entitlement now.
     *
     * [only] is what keeps the two sources from undoing each other: the Play re-check
     * passes [EntitlementSource.PLAY], so a lapsed subscription cannot revoke a grant an
     * administrator made. An administrator passes null and takes it away regardless,
     * which is the point of doing it by hand.
     *
     * Writes NONE rather than an expiry in the past so the row reads exactly like an
     * account that never subscribed - there is nothing to learn from the difference, and
     * a stale expiry left lying about invites somebody to display it.
     */
    fun revoke(user: User, source: EntitlementSource, only: EntitlementSource? = null): User {
        if (only != null && user.entitlementSource != only) {
            log.info("Left user {}'s {} entitlement alone; {} asked to revoke", user.id, user.entitlementSource, source)
            return user
        }
        if (user.entitlement == Entitlement.FREE && user.entitlementSource == EntitlementSource.NONE) return user
        user.entitlement = Entitlement.FREE
        user.entitlementExpiresAt = null
        user.entitlementSource = EntitlementSource.NONE
        log.info("Revoked premium from user {} ({})", user.id, source)
        return userRepository.save(user)
    }

    /**
     * Whether what the account already has outlasts an offer of [until].
     *
     * Null expiry means forever on both sides, so a standing entitlement beats any dated
     * offer and no dated offer beats a standing one.
     */
    private fun outlastsGrant(user: User, until: Instant?): Boolean {
        if (!user.isPremiumNow()) return false
        val current = user.entitlementExpiresAt ?: return true
        return until != null && current.isAfter(until)
    }

    private fun User.isPremiumNow(): Boolean =
        entitlement == Entitlement.PREMIUM && entitlementExpiresAt?.isAfter(now()) != false

    /** Now, from the injected clock, so tests can move it. */
    fun now(): Instant = Instant.now(clock)
}
