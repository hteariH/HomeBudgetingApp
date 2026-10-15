package com.homebudget.app.household

import com.homebudget.app.bank.QrCode
import com.homebudget.app.domain.User
import com.homebudget.app.repository.HouseholdMemberRepository
import com.homebudget.app.security.SecurityUtils
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Invitations into the signed-in owner's household.
 *
 * The raw token leaves the server exactly once, in the response to [create]; afterwards
 * only its hash exists, so a leaked database is not a set of working links.
 */
@Service
@Transactional
class InviteService(
    private val invites: HouseholdInviteRepository,
    private val members: HouseholdMemberRepository,
    private val securityUtils: SecurityUtils,
    private val clock: Clock,
    private val em: EntityManager
) {
    companion object {
        val TTL: Duration = Duration.ofDays(7)
        const val MAX_LIVE = 10
        private val random = SecureRandom()

        fun hash(raw: String): String =
            MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }

        private fun fullName(user: User?) = user?.let { "${it.firstName} ${it.lastName}".trim() }
    }

    fun create(origin: String?): CreatedInvite {
        val me = securityUtils.getCurrentUser()
        val membership = members.findFirstByUserId(me.id) ?: throw IllegalStateException("No household")
        requireOwner(membership)
        val now = Instant.now(clock)
        if (invites.countLive(membership.household.id, now) >= MAX_LIVE) {
            throw InviteLimitException("This household already has $MAX_LIVE open invitations. Revoke one first.")
        }
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val saved = invites.save(
            HouseholdInvite(
                household = membership.household,
                tokenHash = hash(raw),
                createdBy = me,
                createdAt = now,
                expiresAt = now.plus(TTL)
            )
        )
        val path = "/join?token=$raw"
        val qr = origin?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { QrCode.rows(it + path) }
        return CreatedInvite(saved.id, path, saved.expiresAt, qr)
    }

    @Transactional(readOnly = true)
    fun live(): List<InviteSummary> {
        val membership = members.findFirstByUserId(securityUtils.getCurrentUser().id) ?: return emptyList()
        requireOwner(membership)
        return invites.findLive(membership.household.id, Instant.now(clock))
            .map { InviteSummary(it.id, it.createdAt, it.expiresAt, fullName(it.createdBy)) }
    }

    fun revoke(id: Long) {
        val membership = members.findFirstByUserId(securityUtils.getCurrentUser().id)
            ?: throw IllegalStateException("No household")
        requireOwner(membership)
        val invite = invites.findById(id).orElse(null)
            ?.takeIf { it.household.id == membership.household.id }
            ?: throw NoSuchElementException("Invitation not found: $id")
        if (invite.revokedAt == null) invites.save(invite.apply { revokedAt = Instant.now(clock) })
    }

    @Transactional(readOnly = true)
    fun preview(rawToken: String): InvitePreview {
        val invite = requireLive(rawToken)
        return InvitePreview(invite.household.name, fullName(invite.createdBy), invite.expiresAt)
    }

    /** 404 for a token that never existed, 410 for one that did and no longer works. */
    @Transactional(readOnly = true)
    fun requireLive(rawToken: String): HouseholdInvite {
        val invite = invites.findByTokenHash(hash(rawToken))
            ?: throw NoSuchElementException("This invitation link does not work")
        val now = Instant.now(clock)
        when {
            invite.revokedAt != null -> throw InviteGoneException("This invitation was withdrawn")
            invite.usedAt != null -> throw InviteGoneException("This invitation has already been used")
            !invite.expiresAt.isAfter(now) -> throw InviteGoneException("This invitation has expired")
        }
        return invite
    }

    /**
     * The same invite re-read inside the caller's transaction, refused if it stopped being
     * live. Callers hold its household's lock, so a concurrent use has committed by now;
     * the refresh makes sure a copy this transaction loaded earlier does not hide it.
     */
    fun refreshLive(id: Long): HouseholdInvite {
        val invite = invites.findById(id).orElseThrow { NoSuchElementException("This invitation link does not work") }
        em.refresh(invite)
        if (!invite.isLive(Instant.now(clock))) throw InviteGoneException("This invitation is no longer valid")
        return invite
    }

    fun markUsed(invite: HouseholdInvite, user: User) {
        invites.save(invite.apply { usedAt = Instant.now(clock); usedBy = user })
    }
}
