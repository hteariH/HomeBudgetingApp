package com.homebudget.app.oauth

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.User
import com.homebudget.app.token.AccessToken
import com.homebudget.app.token.AccessTokenKind
import com.homebudget.app.token.AccessTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:oauth-cleanup;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true",
    "app.base-url=https://money.example.com"
])
class OAuthCleanupTest {

    @Autowired private lateinit var cleanup: OAuthCleanup
    @Autowired private lateinit var clients: OAuthClientRepository
    @Autowired private lateinit var grants: OAuthGrantRepository
    @Autowired private lateinit var codes: OAuthCodeRepository
    @Autowired private lateinit var tokens: AccessTokenRepository
    @Autowired private lateinit var identity: TestIdentity

    private fun client(age: Duration, everIssued: Boolean = false) = clients.save(
        OAuthClient(
            clientId = UUID.randomUUID().toString(), clientName = "c", redirectUris = "https://claude.ai/cb",
            createdAt = Instant.now().minus(age),
            lastUsedAt = if (everIssued) Instant.now().minus(age).plusSeconds(60) else null
        )
    )

    private fun grant(client: OAuthClient, user: User, revokedAgo: Duration? = null) = grants.save(
        OAuthGrant(client = client, user = user, scope = "ledger:write", revokedAt = revokedAgo?.let { Instant.now().minus(it) })
    )

    private fun code(grant: OAuthGrant, expiredAgo: Duration) = codes.save(
        OAuthCode(
            codeHash = UUID.randomUUID().toString().replace("-", ""), grant = grant, redirectUri = "https://claude.ai/cb",
            codeChallenge = "c".repeat(43), expiresAt = Instant.now().minus(expiredAgo)
        )
    )

    /** A token of [grant] whose expiry is [expiresIn] from now (negative: in the past). */
    private fun token(grant: OAuthGrant, kind: AccessTokenKind, expiresIn: Duration, used: Boolean = false) = tokens.save(
        AccessToken(
            user = grant.user, kind = kind, name = "c", tokenHash = UUID.randomUUID().toString().replace("-", "") + "00000000",
            prefix = "pma_xxx", oauthGrantId = grant.id, expiresAt = Instant.now().plus(expiresIn),
            usedAt = if (used) Instant.now().minusSeconds(60) else null
        )
    )

    @Test
    fun `deletes clients that never obtained a token after a day, and codes expired over an hour ago`() {
        val user = identity.newMember("cleanup").user
        val stale = client(Duration.ofHours(25))
        val fresh = client(Duration.ofHours(23))
        // Approved, but the code was never exchanged: goes with its grant.
        val approvedNeverExchanged = client(Duration.ofHours(25))
        val orphanGrant = grant(approvedNeverExchanged, user)
        val used = client(Duration.ofDays(30), everIssued = true)
        val grant = grant(used, user)
        val oldCode = code(grant, Duration.ofMinutes(61))
        val recentCode = code(grant, Duration.ofMinutes(59))

        cleanup.run()

        assertNull(clients.findByClientId(stale.clientId))
        assertNotNull(clients.findByClientId(fresh.clientId))
        assertNull(clients.findByClientId(approvedNeverExchanged.clientId))
        assertFalse(grants.existsById(orphanGrant.id))
        assertNotNull(clients.findByClientId(used.clientId))
        assertEquals(false, codes.existsById(oldCode.id))
        assertEquals(true, codes.existsById(recentCode.id))
    }

    @Test
    fun `prunes OAuth tokens a day past expiry and the tokens of grants revoked a day ago`() {
        val user = identity.newMember("prune").user
        val client = client(Duration.ofDays(100), everIssued = true)
        val live = grant(client, user)
        val longExpired = token(live, AccessTokenKind.OAUTH_ACCESS, Duration.ofHours(-25))
        val justExpired = token(live, AccessTokenKind.OAUTH_ACCESS, Duration.ofHours(-23))
        val oldRefresh = token(live, AccessTokenKind.OAUTH_REFRESH, Duration.ofHours(-25), used = true)
        // Rotated but not expired: kept, so presenting it again still revokes the grant.
        val rotated = token(live, AccessTokenKind.OAUTH_REFRESH, Duration.ofDays(80), used = true)
        val current = token(live, AccessTokenKind.OAUTH_REFRESH, Duration.ofDays(89))

        val revokedLongAgo = grant(client, user, revokedAgo = Duration.ofHours(25))
        val deadRefresh = token(revokedLongAgo, AccessTokenKind.OAUTH_REFRESH, Duration.ofDays(89))
        val revokedRecently = grant(client, user, revokedAgo = Duration.ofHours(23))
        val recentRefresh = token(revokedRecently, AccessTokenKind.OAUTH_REFRESH, Duration.ofDays(89))

        cleanup.run()

        assertFalse(tokens.existsById(longExpired.id))
        assertTrue(tokens.existsById(justExpired.id))
        assertFalse(tokens.existsById(oldRefresh.id))
        assertTrue(tokens.existsById(rotated.id))
        assertTrue(tokens.existsById(current.id))
        assertFalse(tokens.existsById(deadRefresh.id))
        assertTrue(tokens.existsById(recentRefresh.id))
    }
}
