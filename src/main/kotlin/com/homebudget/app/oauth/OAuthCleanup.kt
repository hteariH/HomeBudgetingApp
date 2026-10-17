package com.homebudget.app.oauth

import com.homebudget.app.token.AccessTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Hourly:
 * - forgets clients that obtained no token within a day of registering (registration is open,
 *   so anyone can make them), with any grant approved but never exchanged;
 * - codes that expired over an hour ago (a code is good for a minute; keeping it a while longer
 *   lets a late second presentation still revoke its grant);
 * - OAuth tokens a day past their expiry, and all tokens of grants revoked over a day ago, so
 *   rotation does not pile up rows. Rotated refresh tokens stay until they expire.
 */
@Component
class OAuthCleanup(
    private val clients: OAuthClientRepository,
    private val codes: OAuthCodeRepository,
    private val tokens: AccessTokenRepository,
    private val clock: Clock
) {
    companion object {
        val KEEP_UNUSED_CLIENT: Duration = Duration.ofHours(24)
        val KEEP_EXPIRED_CODE: Duration = Duration.ofHours(1)
        val KEEP_DEAD_TOKEN: Duration = Duration.ofDays(1)
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    fun run() {
        val now = Instant.now(clock)
        val removedClients = clients.deleteUnusedBefore(now.minus(KEEP_UNUSED_CLIENT))
        val removedCodes = codes.deleteExpiredBefore(now.minus(KEEP_EXPIRED_CODE))
        val removedTokens = tokens.deleteStaleOAuth(now.minus(KEEP_DEAD_TOKEN))
        if (removedClients > 0) log.info("Deleted {} OAuth clients nobody approved within a day", removedClients)
        if (removedCodes > 0) log.debug("Deleted {} expired OAuth codes", removedCodes)
        if (removedTokens > 0) log.debug("Deleted {} expired or revoked OAuth tokens", removedTokens)
    }
}
