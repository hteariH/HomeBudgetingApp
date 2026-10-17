package com.homebudget.app.token

import com.homebudget.app.domain.User
import com.homebudget.app.oauth.OAuthGrant
import com.homebudget.app.oauth.OAuthGrantRepository
import com.homebudget.app.security.SecurityUtils
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Personal access tokens, the OAuth grants listed beside them, and the check every token
 * presented on `/mcp` goes through.
 *
 * The raw token exists only in the response that hands it out; afterwards it is matched by
 * its SHA-256.
 */
@Service
@Transactional
class AccessTokenService(
    private val repo: AccessTokenRepository,
    private val grants: OAuthGrantRepository,
    private val securityUtils: SecurityUtils,
    private val clock: Clock,
    private val events: ApplicationEventPublisher
) {
    companion object {
        const val PERSONAL_PREFIX = "pm_"
        /** How much of the token lists show. */
        const val SHOWN_PREFIX_LENGTH = 7
        val EXPIRY_OPTIONS = setOf(30, 90, 365)
        const val MAX_NAME_LENGTH = 100
        /** `last_used_at` is written at most this often, so a busy assistant is not a write per call. */
        private val LAST_USED_RESOLUTION: Duration = Duration.ofMinutes(1)
    }

    /** A new personal token for the signed-in user; [expiresInDays] null means it never expires. */
    fun createPersonal(name: String, expiresInDays: Int?): CreatedToken {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_NAME_LENGTH) {
            "A token needs a name of 1 to $MAX_NAME_LENGTH characters"
        }
        require(expiresInDays == null || expiresInDays in EXPIRY_OPTIONS) {
            "A token expires in 30, 90 or 365 days, or never"
        }
        val now = Instant.now(clock)
        val raw = newToken(PERSONAL_PREFIX)
        val saved = repo.save(
            AccessToken(
                user = securityUtils.getCurrentUser(),
                kind = AccessTokenKind.PERSONAL,
                name = trimmed,
                tokenHash = sha256Hex(raw),
                prefix = raw.take(SHOWN_PREFIX_LENGTH),
                createdAt = now,
                expiresAt = expiresInDays?.let { now.plus(Duration.ofDays(it.toLong())) }
            )
        )
        return CreatedToken(raw, saved.toView())
    }

    /** The user's personal tokens and live OAuth grants, newest first. */
    @Transactional(readOnly = true)
    fun listFor(user: User): List<TokenView> {
        val personal = repo.findAllByUserIdAndKindOrderByCreatedAtDescIdDesc(user.id, AccessTokenKind.PERSONAL).map { it.toView() }
        val oauth = grants.findConnectedByUser(user.id).map { grant ->
            TokenView(
                id = grant.uuid,
                kind = TokenKind.OAUTH,
                name = grant.client.clientName,
                prefix = null,
                createdAt = grant.createdAt,
                lastUsedAt = repo.lastUsedByGrant(grant.id),
                expiresAt = null
            )
        }
        return (personal + oauth).sortedByDescending { it.createdAt }
    }

    /**
     * Revokes one of the signed-in user's items: a personal token (numeric id) is deleted, an
     * OAuth grant (uuid) is marked revoked. Anyone else's is reported as not found.
     */
    fun revoke(id: String) {
        val userId = securityUtils.getCurrentUser().id
        val numeric = id.toLongOrNull()
        if (numeric == null) {
            revokeGrant(grants.findByUuidAndUserIdAndRevokedAtIsNull(id, userId) ?: throw NoSuchElementException("Token not found"))
            return
        }
        val token = repo.findByIdAndUserIdAndKind(numeric, userId, AccessTokenKind.PERSONAL)
            ?: throw NoSuchElementException("Token not found")
        repo.delete(token)
        events.publishEvent(TokenRevokedEvent(token.id))
    }

    /**
     * Stops an OAuth grant and with it every token it issued, closing whatever they hold open.
     * Revoking a revoked grant changes nothing but closes again, which is harmless.
     */
    fun revokeGrant(grant: OAuthGrant) {
        val managed = grants.findById(grant.id).orElse(null) ?: return
        if (managed.revokedAt == null) managed.revokedAt = Instant.now(clock)
        repo.findIdsByGrant(managed.id).forEach { events.publishEvent(TokenRevokedEvent(it)) }
    }

    /**
     * The owner of a token presented on `/mcp`, or null when it is unknown, expired, used,
     * revoked, or a refresh token (which only buys access tokens and never reaches `/mcp`).
     */
    fun authenticate(raw: String): AuthenticatedToken? {
        if (raw.isBlank()) return null
        val token = repo.findByTokenHash(sha256Hex(raw)) ?: return null
        val now = Instant.now(clock)
        if (token.kind == AccessTokenKind.OAUTH_REFRESH) return null
        if (token.expiresAt?.isAfter(now) == false) return null
        if (token.usedAt != null) return null
        if (token.oauthGrantId != null && repo.countLiveGrants(token.oauthGrantId) == 0L) return null
        if (token.lastUsedAt?.isAfter(now.minus(LAST_USED_RESOLUTION)) != true) {
            token.lastUsedAt = now
        }
        return AuthenticatedToken(token.user, token.id)
    }

    private fun AccessToken.toView() = TokenView(
        id = id.toString(),
        kind = TokenKind.PERSONAL,
        name = name,
        prefix = prefix,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        expiresAt = expiresAt
    )
}
