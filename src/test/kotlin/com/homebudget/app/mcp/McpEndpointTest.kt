package com.homebudget.app.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Account
import com.homebudget.app.domain.AccountType
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.security.JwtUtils
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.CategoryService
import com.homebudget.app.token.AccessTokenRepository
import com.homebudget.app.token.AccessTokenService
import com.homebudget.app.mcp.tools.References
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** The MCP endpoint over real HTTP, the way an assistant reaches it. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestIdentity::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:mcp-endpoint;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true",
    "app.base-url=https://money.example.com"
])
class McpEndpointTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var tokens: AccessTokenService
    @Autowired private lateinit var tokenRepo: AccessTokenRepository
    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var currencies: CurrencyRepository
    @Autowired private lateinit var references: References
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var categoryService: CategoryService
    @Autowired private lateinit var sessions: McpSessions
    @Autowired private lateinit var jwt: JwtUtils
    @Autowired private lateinit var membership: com.homebudget.app.household.MembershipService
    @Autowired private lateinit var householdService: com.homebudget.app.service.HouseholdService
    @Autowired private lateinit var members: com.homebudget.app.repository.HouseholdMemberRepository

    private val http = HttpClient.newHttpClient()
    private lateinit var me: UserPrincipal
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        me = identity.newMember("mcp")
        account(me, "Card", "UAH")
        account(me, "Cash", "USD")
        categoryService.seedDefaultCategories(me.household)
        token = tokenFor(me)
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun account(owner: UserPrincipal, name: String, currency: String): Account = accounts.save(
        Account(name = name, type = AccountType.CHECKING, household = owner.household,
            currency = currencies.findByCode(currency)!!, initialBalanceMinor = 12345)
    )

    private fun tokenFor(owner: UserPrincipal): String {
        identity.signIn(owner)
        try {
            return tokens.createPersonal("Claude Desktop", null).token
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    // ---- a tiny MCP client -------------------------------------------------------------

    private class Session(val id: String?)

    private fun post(token: String?, body: String, sessionId: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        token?.let { request.header("Authorization", "Bearer $it") }
        sessionId?.let { request.header("Mcp-Session-Id", it) }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun request(token: String, sessionId: String) = HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
        .header("Authorization", "Bearer $token")
        .header("Mcp-Session-Id", sessionId)

    private fun get(token: String, sessionId: String): HttpResponse<String> = http.send(
        request(token, sessionId).header("Accept", "text/event-stream").timeout(Duration.ofSeconds(5)).GET().build(),
        HttpResponse.BodyHandlers.ofString()
    )

    private fun delete(token: String, sessionId: String): HttpResponse<String> =
        http.send(request(token, sessionId).DELETE().build(), HttpResponse.BodyHandlers.ofString())

    /** Opens the session's SSE stream; the future completes once the stream has ended. */
    private fun openStream(token: String, sessionId: String): CompletableFuture<ByteArray> {
        val before = sessions.openStreams()
        val future = http.sendAsync(
            request(token, sessionId).header("Accept", "text/event-stream").GET().build(),
            HttpResponse.BodyHandlers.ofInputStream()
        ).thenApply { response -> response.body().use(InputStream::readAllBytes) }
        val deadline = System.currentTimeMillis() + 5000
        while (sessions.openStreams() == before) {
            check(System.currentTimeMillis() < deadline) { "the stream never opened" }
            Thread.sleep(20)
        }
        return future
    }

    /** The JSON-RPC response in the body, whether the transport answered with JSON or SSE. */
    private fun rpcResult(response: HttpResponse<String>): JsonNode {
        val type = response.headers().firstValue("Content-Type").orElse("")
        val json = if (type.contains("text/event-stream")) {
            response.body().lines().filter { it.startsWith("data:") }
                .map { mapper.readTree(it.removePrefix("data:").trim()) }
                .first { it.has("id") }
        } else {
            mapper.readTree(response.body())
        }
        assertFalse(json.has("error"), "JSON-RPC error: $json")
        return json["result"]
    }

    private fun initialize(token: String): Session {
        val response = post(token, """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
             "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
        """.trimIndent())
        assertEquals(200, response.statusCode(), response.body())
        val session = Session(response.headers().firstValue("Mcp-Session-Id").orElse(null))
        assertNotNull(session.id, "initialize returns Mcp-Session-Id")
        assertEquals("personalmoney", rpcResult(response)["serverInfo"]["name"].asText())
        val initialized = post(token, """{"jsonrpc":"2.0","method":"notifications/initialized"}""", session.id)
        assertTrue(initialized.statusCode() in 200..299, "notifications/initialized: ${initialized.statusCode()}")
        return session
    }

    private fun callTool(token: String, session: Session, name: String, args: String = "{}", id: Int = 3) =
        post(token, """{"jsonrpc":"2.0","id":$id,"method":"tools/call","params":{"name":"$name","arguments":$args}}""", session.id)

    private fun toolJson(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        val result = rpcResult(response)
        assertFalse(result["isError"]?.asBoolean() ?: false, "tool error: $result")
        return mapper.readTree(result["content"][0]["text"].asText())
    }

    // ---- tests ------------------------------------------------------------------------

    @Test
    fun `initialize, list the tools and list the caller's accounts`() {
        val session = initialize(token)

        val list = rpcResult(post(token, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", session.id))
        assertEquals(setOf("list_accounts", "list_categories", "search_transactions", "summarize_spending", "create_transaction", "create_transfer"), list["tools"].map { it["name"].asText() }.toSet())

        val listed = toolJson(callTool(token, session, "list_accounts"))
        assertEquals(listOf("Card", "Cash"), listed.map { it["name"].asText() })
        val card = listed.first { it["name"].asText() == "Card" }
        assertEquals("UAH", card["currency"].asText())
        assertEquals("123.45", card["balance"].asText())
        assertEquals("CHECKING", card["type"].asText())
        assertFalse(card["archived"].asBoolean())
        assertEquals(36, card["uuid"].asText().length)
    }

    @Test
    fun `list_categories returns the household's categories`() {
        val session = initialize(token)
        val categories = toolJson(callTool(token, session, "list_categories", """{"type":"INCOME"}"""))
        assertEquals(listOf("Salary", "Freelance"), categories.map { it["name"].asText() })
        assertTrue(categories.all { it["type"].asText() == "INCOME" && it["parentUuid"] == null })
    }

    @Test
    fun `another household's token sees only its own accounts`() {
        val other = identity.newMember("other")
        account(other, "Theirs", "EUR")
        val otherToken = tokenFor(other)

        val listed = toolJson(callTool(otherToken, initialize(otherToken), "list_accounts"))
        assertEquals(listOf("Theirs"), listed.map { it["name"].asText() })
    }

    @Test
    fun `no token is a 401 that points at the protected resource metadata`() {
        val response = post(null, """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
        assertEquals(401, response.statusCode())
        assertEquals(
            "Bearer resource_metadata=\"https://money.example.com/.well-known/oauth-protected-resource\"",
            response.headers().firstValue("WWW-Authenticate").orElse(null)
        )
    }

    @Test
    fun `a revoked token is refused`() {
        val session = initialize(token)
        identity.signIn(me)
        tokens.revoke(tokens.listFor(me.user).single().id)
        SecurityContextHolder.clearContext()

        assertEquals(401, callTool(token, session, "list_accounts").statusCode())
    }

    @Test
    fun `an expired token is refused`() {
        val stored = tokenRepo.findAll().first { it.user.id == me.user.id }
        tokenRepo.save(stored.apply { expiresAt = Instant.now().minusSeconds(1) })

        assertEquals(401, post(token, """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""").statusCode())
    }

    @Test
    fun `the 61st tool call within a minute is refused`() {
        val session = initialize(token)
        repeat(60) { assertEquals(200, callTool(token, session, "list_accounts", id = 10 + it).statusCode()) }
        assertEquals(429, callTool(token, session, "list_accounts", id = 99).statusCode())
        // Other methods do not count and are not refused.
        assertEquals(200, post(token, """{"jsonrpc":"2.0","id":100,"method":"tools/list"}""", session.id).statusCode())
    }

    @Test
    fun `an ambiguous account name lists the candidates`() {
        val uah = accounts.findAllByHousehold(me.household).first { it.name == "Card" }
        val usd = account(me, "card", "USD")

        val error = assertThrows<ToolInputException> {
            McpPrincipalScope.runAs(me) { references.account("CARD") }
        }
        assertEquals(
            "Account \"CARD\" matches 2 accounts: Card (UAH) ${uah.uuid}, card (USD) ${usd.uuid} — pass the uuid",
            error.message
        )
        assertEquals(
            "Account \"Nope\" not found — list_accounts shows the accounts",
            assertThrows<ToolInputException> { McpPrincipalScope.runAs(me) { references.account("Nope") } }.message
        )
        assertEquals(usd.id, McpPrincipalScope.runAs(me) { references.account(usd.uuid) }.id)
        assertEquals(null, SecurityContextHolder.getContext().authentication, "runAs leaves no principal behind")
    }

    @Test
    fun `a session answers only the user who opened it`() {
        val session = initialize(token)
        val other = identity.newMember("intruder")
        val otherToken = tokenFor(other)
        val list = """{"jsonrpc":"2.0","id":2,"method":"tools/list"}"""

        assertEquals(404, post(otherToken, list, session.id).statusCode())
        assertEquals(404, get(otherToken, session.id!!).statusCode())
        assertEquals(404, delete(otherToken, session.id).statusCode())
        assertEquals(404, post(token, list, "not-a-session").statusCode())

        assertEquals(200, post(token, list, session.id).statusCode())
        assertEquals(200, delete(token, session.id).statusCode())
        assertEquals(404, post(token, list, session.id).statusCode(), "deleted")
    }

    @Test
    fun `revoking the token ends its open stream`() {
        val session = initialize(token)
        val stream = openStream(token, session.id!!)

        identity.signIn(me)
        tokens.revoke(tokens.listFor(me.user).single().id)
        SecurityContextHolder.clearContext()

        stream.get(5, TimeUnit.SECONDS)
        assertEquals(401, post(token, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", session.id).statusCode())
    }

    @Test
    fun `a web sign-in token does not open the endpoint`() {
        val response = post(jwt.generateToken(me), """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
        assertEquals(401, response.statusCode())
    }

    @Test
    fun `reference errors quote the name without its surrounding spaces`() {
        assertEquals(
            "Account \"Nope\" not found — list_accounts shows the accounts",
            assertThrows<ToolInputException> { McpPrincipalScope.runAs(me) { references.account("  Nope ") } }.message
        )
        assertEquals(
            "Category \"Nope\" not found — list_categories shows the categories",
            assertThrows<ToolInputException> { McpPrincipalScope.runAs(me) { references.category(" Nope") } }.message
        )
    }

    @Test
    fun `a session follows its user into a new household`() {
        val member = identity.joinAs("mover", me.household)
        val memberToken = tokenFor(member)
        val session = initialize(memberToken)
        assertEquals(listOf("Card", "Cash"), toolJson(callTool(memberToken, session, "list_accounts")).map { it["name"].asText() })

        identity.signIn(member)
        try { membership.leave() } finally { SecurityContextHolder.clearContext() }
        val moved = UserPrincipal(member.user, householdService.householdOf(member.user))
        account(moved, "Own wallet", "EUR")

        val listed = toolJson(callTool(memberToken, session, "list_accounts", id = 4))
        assertEquals(listOf("Own wallet"), listed.map { it["name"].asText() })
    }

    @Test
    fun `a user who belongs to no household is refused, not failed`() {
        members.delete(members.findFirstByUserId(me.user.id)!!)

        val response = post(token, """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
        assertEquals(401, response.statusCode())
    }
}
