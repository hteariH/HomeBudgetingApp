package com.homebudget.app.mcp

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.InstanceSetting
import com.homebudget.app.domain.Role
import com.homebudget.app.repository.InstanceSettingRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.token.AccessTokenService
import org.springframework.security.core.context.SecurityContextHolder
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private fun postMcp(port: Int): HttpResponse<String> = HttpClient.newHttpClient().send(
    HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
        .header("Accept", "application/json, text/event-stream")
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer pm_" + "a".repeat(40))
        .POST(HttpRequest.BodyPublishers.ofString("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""))
        .build(),
    HttpResponse.BodyHandlers.ofString()
)

/** The administrator's switch, with nothing set in the environment. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:mcp-switch;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=",
    "app.base-url="
])
class McpSwitchTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var settings: InstanceSettingRepository
    @Autowired private lateinit var tokens: AccessTokenService
    @Autowired private lateinit var sessions: McpSessions

    private lateinit var admin: UserPrincipal

    @BeforeEach
    fun setUp() {
        val made = identity.newMember("admin")
        users.save(made.user.apply { role = Role.ADMIN })
        admin = UserPrincipal(made.user, made.household)
    }

    @AfterEach
    fun tearDown() {
        if (settings.existsById(McpSettings.KEY)) settings.deleteById(McpSettings.KEY)
    }

    private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    @Test
    fun `off by default - the endpoint and token creation answer 404`() {
        val response = postMcp(port)
        assertEquals(404, response.statusCode())
        assertTrue(response.body().isEmpty(), "empty body: ${response.body()}")
        mvc.perform(
            post("/api/assistants/tokens").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON).content("""{"name":"t"}""")
        ).andExpect(status().isNotFound)
        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.mcpEnabled").value(false))
            .andExpect(jsonPath("$.oauthAvailable").value(false))
            .andExpect(jsonPath("$.baseUrl").doesNotExist())
        mvc.perform(get("/api/admin/mcp").with(asUser(admin)))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.lockedByEnvironment").value(false))
    }

    @Test
    fun `while off, tokens made earlier can still be listed and revoked`() {
        identity.signIn(admin)
        val made = try { tokens.createPersonal("Left over", null) } finally { SecurityContextHolder.clearContext() }

        mvc.perform(get("/api/assistants/tokens").with(asUser(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Left over"))
        mvc.perform(delete("/api/assistants/tokens/${made.item.id}").with(asUser(admin))).andExpect(status().isNoContent)
        mvc.perform(get("/api/assistants/tokens").with(asUser(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `the administrator turns it on`() {
        mvc.perform(
            put("/api/admin/mcp").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":true}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.enabled").value(true))

        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.mcpEnabled").value(true))
            // OAuth needs APP_BASE_URL, which is not set here.
            .andExpect(jsonPath("$.oauthAvailable").value(false))
            .andExpect(jsonPath("$.baseUrl").doesNotExist())
        mvc.perform(get("/api/assistants/tokens").with(asUser(admin))).andExpect(status().isOk)
        // On, so a bad token is now a 401 rather than a 404, with a plain challenge.
        val response = postMcp(port)
        assertEquals(401, response.statusCode())
        assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(null))
    }

    @Test
    fun `turning it off ends open streams and closes existing sessions`() {
        mvc.perform(
            put("/api/admin/mcp").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":true}""")
        ).andExpect(status().isOk)
        identity.signIn(admin)
        val token = try { tokens.createPersonal("t", null).token } finally { SecurityContextHolder.clearContext() }
        val http = HttpClient.newHttpClient()
        fun mcp(body: String, session: String? = null) = HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .apply { session?.let { header("Mcp-Session-Id", it) } }
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val init = http.send(
            mcp("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}"""),
            HttpResponse.BodyHandlers.ofString()
        )
        val session = init.headers().firstValue("Mcp-Session-Id").get()
        val before = sessions.openStreams()
        val stream = http.sendAsync(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
                .header("Accept", "text/event-stream").header("Authorization", "Bearer $token")
                .header("Mcp-Session-Id", session).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream()
        ).thenApply { it.body().use { body -> body.readAllBytes() } }
        val deadline = System.currentTimeMillis() + 5000
        while (sessions.openStreams() == before) {
            check(System.currentTimeMillis() < deadline) { "the stream never opened" }
            Thread.sleep(20)
        }

        mvc.perform(
            put("/api/admin/mcp").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":false}""")
        ).andExpect(status().isOk)

        stream.get(5, TimeUnit.SECONDS)
        val after = http.send(mcp("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", session), HttpResponse.BodyHandlers.ofString())
        assertEquals(404, after.statusCode())
    }

    @Test
    fun `only an administrator flips it`() {
        val member = identity.newMember("member")
        mvc.perform(
            put("/api/admin/mcp").with(asUser(member)).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":true}""")
        ).andExpect(status().isForbidden)
        assertFalse(settings.existsById(McpSettings.KEY))
    }
}

/** `MCP_ENABLED=false` wins over the administrator's switch. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:mcp-switch-locked;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=false",
    "app.base-url=https://money.example.com"
])
class McpSwitchLockedTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var settings: InstanceSettingRepository

    @Test
    fun `the environment beats the page`() {
        settings.save(InstanceSetting(McpSettings.KEY, "true"))
        val made = identity.newMember("admin")
        users.save(made.user.apply { role = Role.ADMIN })
        val admin = UserPrincipal(made.user, made.household)
        val asAdmin = authentication(UsernamePasswordAuthenticationToken(admin, null, admin.authorities))

        assertEquals(404, postMcp(port).statusCode())
        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.mcpEnabled").value(false))
            .andExpect(jsonPath("$.oauthAvailable").value(false))
            // Set, but of no use while MCP is off.
            .andExpect(jsonPath("$.baseUrl").doesNotExist())
        mvc.perform(get("/api/admin/mcp").with(asAdmin))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.lockedByEnvironment").value(true))
        mvc.perform(
            put("/api/admin/mcp").with(asAdmin).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":true}""")
        ).andExpect(status().isConflict)
    }
}

/** Rejected tokens on `/mcp` are capped per address; valid tokens and discovery are not. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:mcp-auth-failures;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true"
])
class McpAuthFailureLimitTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var tokens: AccessTokenService

    private fun post(authorization: String?): HttpResponse<String> = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .apply { authorization?.let { header("Authorization", it) } }
            .POST(HttpRequest.BodyPublishers.ofString(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}"""
            ))
            .build(),
        HttpResponse.BodyHandlers.ofString()
    )

    @Test
    fun `only rejected tokens count, and a valid token still gets through over the cap`() {
        val me = identity.newMember("capped")
        identity.signIn(me)
        val valid = try { tokens.createPersonal("t", null).token } finally { SecurityContextHolder.clearContext() }
        val bad = "Bearer pm_" + "a".repeat(40)

        // Discovery - no Authorization header at all - is never counted.
        repeat(40) { assertEquals(401, post(null).statusCode(), "discovery ${it + 1}") }

        repeat(30) { assertEquals(401, post(bad).statusCode(), "attempt ${it + 1}") }
        val refused = post(bad)
        assertEquals(429, refused.statusCode())
        assertTrue(refused.headers().firstValue("Retry-After").isPresent)

        assertEquals(200, post("Bearer $valid").statusCode(), "a valid token is never capped")
        val discovery = post(null)
        assertEquals(401, discovery.statusCode())
        assertEquals("Bearer", discovery.headers().firstValue("WWW-Authenticate").orElse(null))
    }
}
