package com.homebudget.app.service

import com.homebudget.app.domain.RefreshToken
import com.homebudget.app.domain.User
import com.homebudget.app.repository.RefreshTokenRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64

/**
 * Issues and redeems refresh tokens.
 *
 * The access token is deliberately short-lived, which only works if there is something
 * to renew it with - a mobile client cannot ask for a password every few minutes, and a
 * 24-hour access token with no renewal was the previous alternative.
 *
 * Only the SHA-256 of each token is stored. The table is then a list of live sessions
 * rather than a list of working credentials.
 */
@Service
class RefreshTokenService(
    private val repo: RefreshTokenRepository,
    private val clock: Clock,
    @Value("\${app.jwt.refreshExpirationMs:5184000000}") private val refreshExpirationMs: Long
) {
    private val random = SecureRandom()

    fun issue(user: User): String {
        val raw = ByteArray(32).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        repo.save(
            RefreshToken(
                user = user,
                tokenHash = hash(token),
                expiresAt = Instant.now(clock).plusMillis(refreshExpirationMs),
                createdAt = Instant.now(clock)
            )
        )
        return token
    }

    /**
     * Redeems a token and issues a replacement, invalidating the old one.
     *
     * Rotating on every use means a stolen token stops working as soon as the real
     * client refreshes, instead of staying valid for its full lifetime.
     */
    @Transactional
    fun rotate(token: String): Pair<User, String> {
        val stored = repo.findByTokenHash(hash(token))
            ?: throw IllegalArgumentException("Invalid refresh token")
        require(!stored.revoked) { "Refresh token has been revoked" }
        require(stored.expiresAt.isAfter(Instant.now(clock))) { "Refresh token has expired" }

        stored.revoked = true
        repo.save(stored)
        return stored.user to issue(stored.user)
    }

    /** Signs the user out everywhere. */
    @Transactional
    fun revokeAll(user: User) {
        repo.revokeAllForUser(user.id)
    }

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
