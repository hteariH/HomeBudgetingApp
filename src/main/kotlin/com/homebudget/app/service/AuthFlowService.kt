package com.homebudget.app.service

import com.homebudget.app.domain.AuthToken
import com.homebudget.app.domain.AuthTokenPurpose
import com.homebudget.app.domain.User
import com.homebudget.app.repository.AuthTokenRepository
import com.homebudget.app.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Email confirmation and password reset.
 *
 * Both are the same shape - mail a single-use secret, redeem it once - so they share
 * one token type and one issuing path.
 */
@Service
class AuthFlowService(
    private val userRepository: UserRepository,
    private val tokenRepository: AuthTokenRepository,
    private val refreshTokenService: RefreshTokenService,
    private val mailService: MailService,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    private companion object {
        val VERIFICATION_TTL: Duration = Duration.ofHours(24)
        // Short on purpose: this link is a full account takeover while it lives.
        val RESET_TTL: Duration = Duration.ofHours(1)
        const val MIN_PASSWORD_LENGTH = 10
    }

    // ── Email verification ──────────────────────────────────────────────────

    @Transactional
    fun sendVerification(user: User) {
        if (user.emailVerified) return
        val raw = issue(user, AuthTokenPurpose.EMAIL_VERIFICATION, VERIFICATION_TTL)
        mailService.sendVerification(user.email, raw)
    }

    @Transactional
    fun verifyEmail(rawToken: String): User {
        val token = redeem(rawToken, AuthTokenPurpose.EMAIL_VERIFICATION)
        val user = token.user
        user.emailVerified = true
        return userRepository.save(user)
    }

    // ── Password reset ──────────────────────────────────────────────────────

    /**
     * Always succeeds, whether or not the address is registered.
     *
     * Answering differently would turn this form into an oracle for which addresses
     * have accounts - on a public site that is a list worth harvesting.
     */
    @Transactional
    fun requestPasswordReset(email: String) {
        val user = userRepository.findByEmail(email.trim()).orElse(null)
        if (user == null) {
            log.info("Password reset requested for an address with no account")
            return
        }
        val raw = issue(user, AuthTokenPurpose.PASSWORD_RESET, RESET_TTL)
        mailService.sendPasswordReset(user.email, raw)
    }

    @Transactional
    fun resetPassword(rawToken: String, newPassword: String): User {
        require(newPassword.length >= MIN_PASSWORD_LENGTH) {
            "Password must be at least $MIN_PASSWORD_LENGTH characters"
        }
        val token = redeem(rawToken, AuthTokenPurpose.PASSWORD_RESET)
        val user = token.user
        user.passwordHash = passwordEncoder.encode(newPassword)
        // Reaching this point means the address was proven, so confirmation is moot.
        user.emailVerified = true
        val saved = userRepository.save(user)

        // The point of a reset is that control was lost. A session that survives it
        // would leave whoever took the account still signed in.
        refreshTokenService.revokeAll(saved)
        return saved
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun issue(user: User, purpose: AuthTokenPurpose, ttl: Duration): String {
        val now = Instant.now(clock)
        // Only the newest link works; an older one left in an inbox stops being a key.
        tokenRepository.invalidateOutstanding(user.id, purpose, now)

        val raw = ByteArray(32).also { random.nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

        tokenRepository.save(
            AuthToken(
                user = user,
                purpose = purpose,
                tokenHash = hash(raw),
                expiresAt = now.plus(ttl),
                createdAt = now
            )
        )
        return raw
    }

    private fun redeem(rawToken: String, purpose: AuthTokenPurpose): AuthToken {
        val token = tokenRepository.findByTokenHash(hash(rawToken))
            ?: throw IllegalArgumentException("This link is not valid")
        require(token.purpose == purpose) { "This link is not valid" }
        require(token.usedAt == null) { "This link has already been used" }
        require(token.expiresAt.isAfter(Instant.now(clock))) { "This link has expired" }

        token.usedAt = Instant.now(clock)
        tokenRepository.save(token)
        return token
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
