package com.homebudget.app.oauth

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Hourly: forgets clients nobody approved within a day of registering (registration is open,
 * so anyone can make them), and codes that expired over an hour ago (a code is good for a
 * minute; keeping it a while longer lets a late second presentation still revoke its grant).
 */
@Component
class OAuthCleanup(
    private val clients: OAuthClientRepository,
    private val codes: OAuthCodeRepository,
    private val clock: Clock
) {
    companion object {
        val KEEP_UNUSED_CLIENT: Duration = Duration.ofHours(24)
        val KEEP_EXPIRED_CODE: Duration = Duration.ofHours(1)
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    fun run() {
        val now = Instant.now(clock)
        val removedClients = clients.deleteUnusedBefore(now.minus(KEEP_UNUSED_CLIENT))
        val removedCodes = codes.deleteExpiredBefore(now.minus(KEEP_EXPIRED_CODE))
        if (removedClients > 0) log.info("Deleted {} OAuth clients nobody approved within a day", removedClients)
        if (removedCodes > 0) log.debug("Deleted {} expired OAuth codes", removedCodes)
    }
}
