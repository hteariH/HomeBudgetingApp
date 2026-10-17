package com.homebudget.app.token

import com.homebudget.app.TestIdentity
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:access-tokens;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true"
])
class AccessTokenServiceTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var tokens: AccessTokenService
    @Autowired private lateinit var repo: AccessTokenRepository
    @Autowired private lateinit var userService: UserService

    private lateinit var me: UserPrincipal

    @BeforeEach
    fun setUp() {
        me = identity.newMember("tokens")
        identity.signIn(me)
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    @Test
    fun `a personal token is pm_ plus 40 base62 characters and only its hash is stored`() {
        val created = tokens.createPersonal("  Claude Desktop  ", 30)

        assertTrue(Regex("pm_[0-9A-Za-z]{40}").matches(created.token), created.token)
        val stored = repo.findAll().single { it.user.id == me.user.id }
        assertEquals(sha256Hex(created.token), stored.tokenHash)
        assertNotEquals(created.token, stored.tokenHash)
        assertTrue(repo.findAll().none { it.tokenHash.contains(created.token) || it.name.contains(created.token) })
        assertEquals(created.token.take(7), stored.prefix)
        assertEquals(AccessTokenKind.PERSONAL, stored.kind)
        assertEquals("Claude Desktop", stored.name)

        assertEquals(TokenKind.PERSONAL, created.item.kind)
        assertEquals("Claude Desktop", created.item.name)
        assertEquals(created.token.take(7), created.item.prefix)
    }

    @Test
    fun `expiry is 30, 90 or 365 days, or never`() {
        for (days in listOf(30, 90, 365)) {
            val view = tokens.createPersonal("t$days", days).item
            val left = Duration.between(Instant.now(), view.expiresAt)
            assertTrue(left > Duration.ofDays(days.toLong()).minusMinutes(1) && left <= Duration.ofDays(days.toLong()))
        }
        assertNull(tokens.createPersonal("forever", null).item.expiresAt)
        assertThrows<IllegalArgumentException> { tokens.createPersonal("odd", 7) }
        assertThrows<IllegalArgumentException> { tokens.createPersonal("   ", null) }
        assertThrows<IllegalArgumentException> { tokens.createPersonal("x".repeat(101), null) }
    }

    @Test
    fun `the endpoints create, list and revoke, and refuse other expiries with 400`() {
        mvc.perform(
            post("/api/assistants/tokens").with(asUser(me)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Cursor","expiresInDays":90}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.startsWith("pm_")))
            .andExpect(jsonPath("$.item.name").value("Cursor"))
            .andExpect(jsonPath("$.item.kind").value("PERSONAL"))

        mvc.perform(
            post("/api/assistants/tokens").with(asUser(me)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Cursor","expiresInDays":12}""")
        ).andExpect(status().isBadRequest)

        val id = tokens.listFor(me.user).single().id
        mvc.perform(get("/api/assistants/tokens").with(asUser(me)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].token").doesNotExist())
        mvc.perform(delete("/api/assistants/tokens/$id").with(asUser(me))).andExpect(status().isNoContent)
        assertTrue(tokens.listFor(me.user).isEmpty())
    }

    @Test
    fun `revoking someone else's token is a 404`() {
        val other = identity.newMember("other")
        identity.signIn(other)
        val theirs = tokens.createPersonal("theirs", null).item.id
        identity.signIn(me)

        assertThrows<NoSuchElementException> { tokens.revoke(theirs) }
        mvc.perform(delete("/api/assistants/tokens/$theirs").with(asUser(me))).andExpect(status().isNotFound)
        assertEquals(1, tokens.listFor(other.user).size)
    }

    @Test
    fun `authenticate finds the owner and records the use at most once a minute`() {
        val created = tokens.createPersonal("t", null)
        val first = tokens.authenticate(created.token)
        assertNotNull(first)
        assertEquals(me.user.id, first!!.user.id)
        val used = repo.findById(first.tokenId).get().lastUsedAt
        assertNotNull(used)

        tokens.authenticate(created.token)
        assertEquals(used, repo.findById(first.tokenId).get().lastUsedAt, "not rewritten within a minute")

        assertNull(tokens.authenticate("pm_" + "x".repeat(40)))
        assertNull(tokens.authenticate(""))
    }

    @Test
    fun `a personal token is not accepted on the REST API`() {
        val created = tokens.createPersonal("t", null)
        // MockMvc would otherwise carry over the test's own sign-in.
        TestSecurityContextHolder.clearContext()

        mvc.perform(get("/api/accounts").header("Authorization", "Bearer ${created.token}"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
    }

    @Test
    fun `deleting the user deletes their tokens`() {
        val created = tokens.createPersonal("t", null)
        userService.deleteUser(me.user)

        assertTrue(repo.findAll().none { it.user.id == me.user.id })
        assertNull(tokens.authenticate(created.token))
    }
}
