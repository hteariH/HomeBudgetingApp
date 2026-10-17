package com.homebudget.app.oauth

import com.homebudget.app.TestIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    @Autowired private lateinit var identity: TestIdentity

    private fun client(age: Duration) = clients.save(
        OAuthClient(
            clientId = UUID.randomUUID().toString(), clientName = "c", redirectUris = "https://claude.ai/cb",
            createdAt = Instant.now().minus(age)
        )
    )

    private fun code(grant: OAuthGrant, expiredAgo: Duration) = codes.save(
        OAuthCode(
            codeHash = UUID.randomUUID().toString().replace("-", ""), grant = grant, redirectUri = "https://claude.ai/cb",
            codeChallenge = "c".repeat(43), expiresAt = Instant.now().minus(expiredAgo)
        )
    )

    @Test
    fun `deletes clients nobody approved after a day, and codes expired over an hour ago`() {
        val stale = client(Duration.ofHours(25))
        val fresh = client(Duration.ofHours(23))
        val approved = client(Duration.ofDays(30))
        val grant = grants.save(OAuthGrant(client = approved, user = identity.newMember("cleanup").user, scope = "ledger:write"))
        val oldCode = code(grant, Duration.ofMinutes(61))
        val recentCode = code(grant, Duration.ofMinutes(59))

        cleanup.run()

        assertNull(clients.findByClientId(stale.clientId))
        assertNotNull(clients.findByClientId(fresh.clientId))
        assertNotNull(clients.findByClientId(approved.clientId))
        assertEquals(false, codes.existsById(oldCode.id))
        assertEquals(true, codes.existsById(recentCode.id))
    }
}
