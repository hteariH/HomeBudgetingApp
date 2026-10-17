package com.homebudget.app.oauth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.token.AccessTokenKind
import com.homebudget.app.token.AccessTokenRepository
import com.homebudget.app.token.TokenRevokedEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

/** The authorization-code flow a web connector goes through, end to end. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@RecordApplicationEvents
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:oauth-flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true",
    "app.base-url=https://money.example.com/"
])
class OAuthFlowTest {

    companion object {
        const val BASE = "https://money.example.com"
        const val REDIRECT = "https://claude.ai/api/mcp/auth_callback"
        /** Each registration from its own address, clear of the registration limit. */
        private val addresses = java.util.concurrent.atomic.AtomicInteger()
    }

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var codes: OAuthCodeRepository
    @Autowired private lateinit var grants: OAuthGrantRepository
    @Autowired private lateinit var tokenRepo: AccessTokenRepository
    @Autowired private lateinit var events: ApplicationEvents
    @Autowired private lateinit var accessTokens: com.homebudget.app.token.AccessTokenService
    @Autowired private lateinit var authFlow: com.homebudget.app.service.AuthFlowService
    @Autowired private lateinit var sessions: com.homebudget.app.mcp.McpSessions

    private val http = HttpClient.newHttpClient()
    private lateinit var me: UserPrincipal
    private lateinit var clientId: String
    private val verifier = "v".repeat(20) + "-._~" + "0123456789abcdefghijklmnop"
    private val challenge = s256(verifier)

    private fun s256(v: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(v.toByteArray(Charsets.US_ASCII)))

    @BeforeEach
    fun setUp() {
        me = identity.newMember("oauth")
        clientId = register()
    }

    private fun asUser(p: UserPrincipal = me) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

    private fun json(result: ResultActions): JsonNode = mapper.readTree(result.andReturn().response.contentAsString)

    private fun register(redirect: String = REDIRECT): String = json(
        mvc.perform(
            post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""{"client_name":"Claude","redirect_uris":["$redirect"]}""")
                .with { it.remoteAddr = "10.1.${addresses.incrementAndGet() / 200}.${addresses.get() % 200}"; it }
        ).andExpect(status().isCreated)
    )["client_id"].asText()

    private fun query(location: String): Map<String, String?> =
        UriComponentsBuilder.fromUriString(location).build().queryParams.toSingleValueMap()
            .mapValues { java.net.URLDecoder.decode(it.value ?: "", Charsets.UTF_8) }

    private fun decision(approve: Boolean = true, state: String = "xyz", resource: String? = null) =
        post("/api/oauth/authorize").with(asUser()).contentType(MediaType.APPLICATION_JSON).content(
            mapper.writeValueAsString(mapOf(
                "clientId" to clientId, "redirectUri" to REDIRECT, "codeChallenge" to challenge,
                "state" to state, "scope" to "ledger:write", "approve" to approve, "resource" to resource
            ))
        )

    private fun approve(approve: Boolean = true, state: String = "xyz", resource: String? = null): String =
        json(mvc.perform(decision(approve, state, resource)).andExpect(status().isOk))["redirect"].asText()

    private fun code(): String {
        val redirect = approve()
        assertTrue(redirect.startsWith("$REDIRECT?code="), redirect)
        return query(redirect)["code"]!!
    }

    private fun exchange(code: String, verifier: String = this.verifier, resource: String? = null) = mvc.perform(
        post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "authorization_code").param("code", code).param("redirect_uri", REDIRECT)
            .param("client_id", clientId).param("code_verifier", verifier)
            .apply { resource?.let { param("resource", it) } }
    )

    private fun refresh(refreshToken: String) = mvc.perform(
        post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "refresh_token").param("refresh_token", refreshToken).param("client_id", clientId)
    )

    private fun tokens(): JsonNode = json(exchange(code()).andExpect(status().isOk))

    private fun mcp(token: String, body: String, session: String? = null): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $token")
            .apply { session?.let { header("Mcp-Session-Id", it) } }
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
        HttpResponse.BodyHandlers.ofString()
    )

    private fun initialize(token: String): HttpResponse<String> = mcp(token,
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}""")

    /** Opens the session's SSE stream; the future completes once the stream has ended. */
    private fun openStream(token: String): java.util.concurrent.CompletableFuture<ByteArray> {
        val session = initialize(token).headers().firstValue("Mcp-Session-Id").get()
        mcp(token, """{"jsonrpc":"2.0","method":"notifications/initialized"}""", session)
        val before = sessions.openStreams()
        val future = http.sendAsync(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/mcp"))
                .header("Accept", "text/event-stream").header("Authorization", "Bearer $token")
                .header("Mcp-Session-Id", session).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream()
        ).thenApply { response -> response.body().use { it.readAllBytes() } }
        val deadline = System.currentTimeMillis() + 5000
        while (sessions.openStreams() == before) {
            check(System.currentTimeMillis() < deadline) { "the stream never opened" }
            Thread.sleep(20)
        }
        return future
    }

    private fun authorizeUrl(
        redirect: String = REDIRECT, client: String = clientId, method: String? = "S256",
        challenge: String? = this.challenge, responseType: String = "code", scope: String? = null
    ) = get("/oauth/authorize").param("response_type", responseType).param("client_id", client)
        .param("redirect_uri", redirect).param("state", "st 1")
        .apply { challenge?.let { param("code_challenge", it) } }
        .apply { method?.let { param("code_challenge_method", it) } }
        .apply { scope?.let { param("scope", it) } }

    @Test
    fun `register, authorize, approve, exchange, use, refresh, and a reused refresh token revokes the grant`() {
        val location = mvc.perform(authorizeUrl().param("resource", "$BASE/mcp"))
            .andExpect(status().isFound).andReturn().response.getHeader("Location")!!
        assertTrue(location.startsWith("$BASE/oauth/consent?"), location)
        val forwarded = query(location)
        assertEquals(clientId, forwarded["client_id"])
        assertEquals(REDIRECT, forwarded["redirect_uri"])
        assertEquals(challenge, forwarded["code_challenge"])
        assertEquals("st 1", forwarded["state"])
        assertEquals("$BASE/mcp", forwarded["resource"])

        mvc.perform(get("/api/oauth/clients/$clientId").param("redirectUri", REDIRECT).with(asUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clientName").value("Claude"))
            .andExpect(jsonPath("$.redirectHost").value("claude.ai"))

        val redirect = approve(state = "st 1", resource = "$BASE/mcp")
        assertTrue(redirect.startsWith("$REDIRECT?code="), redirect)
        assertEquals("st 1", query(redirect)["state"])

        val first = json(
            exchange(query(redirect)["code"]!!, resource = "$BASE/mcp")
                .andExpect(status().isOk)
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.scope").value("ledger:write"))
        )
        val access = first["access_token"].asText()
        val refreshToken = first["refresh_token"].asText()
        assertTrue(access.startsWith("pma_"), access)
        assertTrue(refreshToken.startsWith("pmr_"), refreshToken)
        assertNull(codes.findAll().firstOrNull { it.usedAt == null })

        val init = initialize(access)
        assertEquals(200, init.statusCode(), init.body())
        val session = init.headers().firstValue("Mcp-Session-Id").get()
        mcp(access, """{"jsonrpc":"2.0","method":"notifications/initialized"}""", session)
        val list = mcp(access, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", session)
        assertEquals(200, list.statusCode())
        assertTrue(list.body().contains("create_transaction"), list.body())
        // A refresh token only buys access tokens.
        assertEquals(401, initialize(refreshToken).statusCode())

        val second = json(refresh(refreshToken).andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store")))
        val access2 = second["access_token"].asText()
        val refresh2 = second["refresh_token"].asText()
        assertNotEquals(access, access2)
        assertNotEquals(refreshToken, refresh2)
        assertEquals(200, initialize(access2).statusCode())

        refresh(refreshToken).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
        assertEquals(401, initialize(access2).statusCode())
        assertEquals(401, initialize(access).statusCode())
        refresh(refresh2).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
        assertNotNull(grants.findAll().single { it.user.id == me.user.id }.revokedAt)
        val revokedIds = events.stream(TokenRevokedEvent::class.java).map { it.tokenId }.toList().toSet()
        val grantTokens = tokenRepo.findAll().filter { it.user.id == me.user.id }.map { it.id }.toSet()
        assertEquals(grantTokens, revokedIds)
    }

    @Test
    fun `a wrong verifier is invalid_grant`() {
        exchange(code(), verifier = "w".repeat(43))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
    }

    @Test
    fun `a second use of a code revokes what the first one bought`() {
        val code = code()
        val first = json(exchange(code).andExpect(status().isOk))
        exchange(code).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
        assertEquals(401, initialize(first["access_token"].asText()).statusCode())
        refresh(first["refresh_token"].asText()).andExpect(jsonPath("$.error").value("invalid_grant"))
    }

    @Test
    fun `an expired code is invalid_grant`() {
        val code = code()
        codes.findAll().forEach { it.expiresAt = Instant.now().minusSeconds(1); codes.save(it) }
        exchange(code).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
    }

    @Test
    fun `token endpoint errors follow RFC 6749`() {
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "password"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("unsupported_grant_type"))
        mvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "authorization_code"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_request"))
        val code = code()
        mvc.perform(
            post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code).param("redirect_uri", REDIRECT)
                .param("client_id", "nobody").param("code_verifier", verifier)
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_client"))
        // Issued to another client.
        val other = register()
        mvc.perform(
            post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code).param("redirect_uri", REDIRECT)
                .param("client_id", other).param("code_verifier", verifier)
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
        exchange(code, resource = "https://other.example.com/mcp")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_target"))
        // Neither the wrong client nor the wrong resource used the code up.
        exchange(code).andExpect(status().isOk)
    }

    @Test
    fun `a failed exchange by the right client uses the code up`() {
        val code = code()
        mvc.perform(
            post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code).param("redirect_uri", "$REDIRECT/x")
                .param("client_id", clientId).param("code_verifier", verifier)
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
        exchange(code).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_grant"))
    }

    @Test
    fun `a refresh naming another resource is invalid_target`() {
        val issued = tokens()
        mvc.perform(
            post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token").param("refresh_token", issued["refresh_token"].asText())
                .param("client_id", clientId).param("resource", "$BASE/other")
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_target"))
        refresh(issued["refresh_token"].asText()).andExpect(status().isOk)
    }

    @Test
    fun `the same code exchanged twice at once - one wins, then the grant is revoked`() {
        val code = code()
        val body = "grant_type=authorization_code&code=" + java.net.URLEncoder.encode(code, Charsets.UTF_8) +
            "&redirect_uri=" + java.net.URLEncoder.encode(REDIRECT, Charsets.UTF_8) +
            "&client_id=$clientId&code_verifier=$verifier"
        val start = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map {
                pool.submit<HttpResponse<String>> {
                    start.await()
                    http.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:$port/oauth/token"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                        HttpResponse.BodyHandlers.ofString()
                    )
                }
            }
            start.countDown()
            val responses = futures.map { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(listOf(200, 400), responses.map { it.statusCode() }.sorted(), responses.map { it.body() }.toString())
            val loser = responses.single { it.statusCode() == 400 }
            assertEquals("invalid_grant", mapper.readTree(loser.body())["error"].asText())
            val winner = mapper.readTree(responses.single { it.statusCode() == 200 }.body())
            assertNotNull(grants.findAll().single { it.user.id == me.user.id }.revokedAt)
            assertEquals(401, initialize(winner["access_token"].asText()).statusCode())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a resource other than the MCP endpoint is invalid_target`() {
        val location = mvc.perform(authorizeUrl().param("resource", "https://evil.example/mcp"))
            .andExpect(status().isFound).andReturn().response.getHeader("Location")!!
        assertTrue(location.startsWith("$REDIRECT?error=invalid_target"), location)
        assertEquals("st 1", query(location)["state"])
        mvc.perform(decision(resource = "$BASE/mcp/")).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_target"))
        assertTrue(grants.findAll().none { it.user.id == me.user.id })
    }

    @Test
    fun `the consent page must name a registered redirect URI`() {
        mvc.perform(get("/api/oauth/clients/$clientId").with(asUser())).andExpect(status().isBadRequest)
        mvc.perform(get("/api/oauth/clients/$clientId").param("redirectUri", "https://evil.example/cb").with(asUser()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `a grant that never obtained tokens is not listed`() {
        code()
        val list = json(mvc.perform(get("/api/assistants/tokens").with(asUser())).andExpect(status().isOk))
        assertTrue(list.none { it["kind"].asText() == "OAUTH" }, list.toString())
    }

    @Test
    fun `last used survives the pruning of old access tokens`() {
        val issued = tokens()
        assertEquals(200, initialize(issued["access_token"].asText()).statusCode())
        refresh(issued["refresh_token"].asText()).andExpect(status().isOk)
        tokenRepo.deleteAll(tokenRepo.findAll().filter { it.user.id == me.user.id && it.kind == AccessTokenKind.OAUTH_ACCESS })
        val list = json(mvc.perform(get("/api/assistants/tokens").with(asUser())).andExpect(status().isOk))
        val item = list.single { it["kind"].asText() == "OAUTH" }
        assertTrue(item["lastUsedAt"].isTextual, item.toString())
    }

    @Test
    fun `deny sends access_denied back`() {
        val redirect = approve(approve = false, state = "a&b")
        assertTrue(redirect.startsWith("$REDIRECT?error=access_denied"), redirect)
        assertEquals("a&b", query(redirect)["state"])
        assertTrue(grants.findAll().none { it.user.id == me.user.id })
    }

    @Test
    fun `a redirect URI that already has a query gets the code appended with an ampersand`() {
        val withQuery = "https://chatgpt.com/connector/oauth/cb?x=1"
        clientId = register(withQuery)
        val redirect = json(
            mvc.perform(
                post("/api/oauth/authorize").with(asUser()).contentType(MediaType.APPLICATION_JSON).content(
                    mapper.writeValueAsString(mapOf(
                        "clientId" to clientId, "redirectUri" to withQuery, "codeChallenge" to challenge,
                        "state" to "s", "scope" to "", "approve" to true
                    ))
                )
            ).andExpect(status().isOk)
        )["redirect"].asText()
        assertTrue(redirect.startsWith("$withQuery&code="), redirect)
    }

    @Test
    fun `approval re-validates the request`() {
        mvc.perform(
            post("/api/oauth/authorize").with(asUser()).contentType(MediaType.APPLICATION_JSON).content(
                mapper.writeValueAsString(mapOf(
                    "clientId" to clientId, "redirectUri" to "https://evil.example/cb", "codeChallenge" to challenge,
                    "state" to "s", "scope" to "ledger:write", "approve" to true
                ))
            )
        ).andExpect(status().isBadRequest)
        mvc.perform(post("/api/oauth/authorize").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized)
        assertTrue(grants.findAll().none { it.user.id == me.user.id })
    }

    @Test
    fun `an unknown redirect URI or client gets a page, never a redirect`() {
        val bad = mvc.perform(authorizeUrl(redirect = "https://evil.example/cb"))
            .andExpect(status().isBadRequest).andReturn().response
        assertNull(bad.getHeader("Location"))
        val unknown = mvc.perform(authorizeUrl(client = "nobody")).andExpect(status().isBadRequest).andReturn().response
        assertNull(unknown.getHeader("Location"))
    }

    @Test
    fun `other authorize errors go back to the client as invalid_request`() {
        for (request in listOf(
            authorizeUrl(challenge = null),
            authorizeUrl(method = "plain"),
            authorizeUrl(method = null),
            authorizeUrl(responseType = "token"),
            authorizeUrl(scope = "admin"),
            authorizeUrl(challenge = challenge + "A"),
            authorizeUrl(challenge = challenge.dropLast(1) + "."),
            authorizeUrl(challenge = challenge.dropLast(1))
        )) {
            val location = mvc.perform(request).andExpect(status().isFound).andReturn().response.getHeader("Location")!!
            assertTrue(location.startsWith("$REDIRECT?error=invalid_request"), location)
            assertEquals("st 1", query(location)["state"])
        }
    }

    @Test
    fun `Settings lists the grant and revoking it stops its tokens`() {
        val issued = tokens()
        val list = json(mvc.perform(get("/api/assistants/tokens").with(asUser())).andExpect(status().isOk))
        val item = list.single { it["kind"].asText() == "OAUTH" }
        assertEquals("Claude", item["name"].asText())
        val grant = grants.findAll().single { it.user.id == me.user.id }
        assertEquals(grant.uuid, item["id"].asText())

        // Someone else cannot.
        val other = identity.newMember("other")
        mvc.perform(delete("/api/assistants/tokens/${grant.uuid}").with(asUser(other))).andExpect(status().isNotFound)

        mvc.perform(delete("/api/assistants/tokens/${grant.uuid}").with(asUser())).andExpect(status().isNoContent)
        assertEquals(401, initialize(issued["access_token"].asText()).statusCode())
        refresh(issued["refresh_token"].asText()).andExpect(jsonPath("$.error").value("invalid_grant"))
        val list2 = json(mvc.perform(get("/api/assistants/tokens").with(asUser())).andExpect(status().isOk))
        assertTrue(list2.none { it["kind"].asText() == "OAUTH" })
        val revokedIds = events.stream(TokenRevokedEvent::class.java).map { it.tokenId }.toList().toSet()
        assertEquals(tokenRepo.findAll().filter { it.oauthGrantId == grant.id }.map { it.id }.toSet(), revokedIds)
    }

    @Test
    fun `revocation endpoint revokes the grant and always answers 200`() {
        val issued = tokens()
        mvc.perform(post("/oauth/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", issued["access_token"].asText()))
            .andExpect(status().isOk)
        assertEquals(401, initialize(issued["access_token"].asText()).statusCode())
        refresh(issued["refresh_token"].asText()).andExpect(jsonPath("$.error").value("invalid_grant"))
        mvc.perform(post("/oauth/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", "pma_nonsense"))
            .andExpect(status().isOk)
        mvc.perform(post("/oauth/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isOk)
    }

    @Test
    fun `a password reset stops personal and OAuth tokens and closes their streams`() {
        identity.signIn(me)
        val personal = try { accessTokens.createPersonal("Desktop", null).token } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext()
        }
        val issued = tokens()
        val access = issued["access_token"].asText()
        val personalStream = openStream(personal)
        val oauthStream = openStream(access)

        val link = authFlow.issueAdminResetLink(me.user)
        authFlow.resetPassword(link.path.substringAfter("token="), "a brand new password")

        personalStream.get(5, java.util.concurrent.TimeUnit.SECONDS)
        oauthStream.get(5, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(401, initialize(personal).statusCode())
        assertEquals(401, initialize(access).statusCode())
        refresh(issued["refresh_token"].asText()).andExpect(jsonPath("$.error").value("invalid_grant"))
        val list = json(mvc.perform(get("/api/assistants/tokens").with(asUser())).andExpect(status().isOk))
        assertEquals(0, list.size(), list.toString())
    }

    @Test
    fun `an OAuth access token is refused outside the MCP endpoint`() {
        val access = tokens()["access_token"].asText()
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer $access")).andExpect(status().isUnauthorized)
        mvc.perform(
            post("/api/oauth/authorize").header("Authorization", "Bearer $access").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf(
                    "clientId" to clientId, "redirectUri" to REDIRECT, "codeChallenge" to challenge,
                    "state" to "s", "scope" to "ledger:write", "approve" to true
                )))
        ).andExpect(status().isUnauthorized)
        mvc.perform(
            post("/api/assistants/tokens").header("Authorization", "Bearer $access").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"sneaky"}""")
        ).andExpect(status().isUnauthorized)
        assertEquals(1, grants.findAll().count { it.user.id == me.user.id })
        assertTrue(tokenRepo.findAll().none { it.user.id == me.user.id && it.kind == AccessTokenKind.PERSONAL })
    }

    @Test
    fun `the instance reports its base URL without a trailing slash`() {
        mvc.perform(get("/api/instance"))
            .andExpect(jsonPath("$.oauthAvailable").value(true))
            .andExpect(jsonPath("$.baseUrl").value(BASE))
    }
}
