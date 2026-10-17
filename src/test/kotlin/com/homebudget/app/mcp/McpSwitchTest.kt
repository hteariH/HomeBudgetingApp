package com.homebudget.app.mcp

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.InstanceSetting
import com.homebudget.app.domain.Role
import com.homebudget.app.repository.InstanceSettingRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
    fun `off by default - the endpoint and the token API answer 404`() {
        val response = postMcp(port)
        assertEquals(404, response.statusCode())
        assertTrue(response.body().isEmpty(), "empty body: ${response.body()}")
        mvc.perform(get("/api/assistants/tokens").with(asUser(admin))).andExpect(status().isNotFound)
        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.mcpEnabled").value(false))
            .andExpect(jsonPath("$.oauthAvailable").value(false))
        mvc.perform(get("/api/admin/mcp").with(asUser(admin)))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.lockedByEnvironment").value(false))
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
        mvc.perform(get("/api/assistants/tokens").with(asUser(admin))).andExpect(status().isOk)
        // On, so a bad token is now a 401 rather than a 404, with a plain challenge.
        val response = postMcp(port)
        assertEquals(401, response.statusCode())
        assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(null))
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
        mvc.perform(get("/api/admin/mcp").with(asAdmin))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.lockedByEnvironment").value(true))
        mvc.perform(
            put("/api/admin/mcp").with(asAdmin).contentType(MediaType.APPLICATION_JSON).content("""{"enabled":true}""")
        ).andExpect(status().isConflict)
    }
}
