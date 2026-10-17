package com.homebudget.app.oauth

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.atomic.AtomicInteger

/** Discovery documents and dynamic client registration. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:oauth-registration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true",
    "app.base-url=https://money.example.com/"
])
class OAuthRegistrationTest {

    companion object {
        private val addresses = AtomicInteger()
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var clients: OAuthClientRepository

    private fun register(body: String, address: String = "10.2.0.${addresses.incrementAndGet()}") = mvc.perform(
        post("/oauth/register").contentType(MediaType.APPLICATION_JSON).content(body)
            .with { it.remoteAddr = address; it }
    )

    private fun uris(vararg uris: String) = """{"redirect_uris":[${uris.joinToString(",") { "\"$it\"" }}]}"""

    @Test
    fun `protected resource metadata, at both addresses`() {
        for (path in listOf("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp")) {
            mvc.perform(get(path)).andExpect(status().isOk)
                .andExpect(jsonPath("$.resource").value("https://money.example.com/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("https://money.example.com"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("ledger:write"))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"))
        }
    }

    @Test
    fun `authorization server metadata`() {
        mvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value("https://money.example.com"))
            .andExpect(jsonPath("$.authorization_endpoint").value("https://money.example.com/oauth/authorize"))
            .andExpect(jsonPath("$.token_endpoint").value("https://money.example.com/oauth/token"))
            .andExpect(jsonPath("$.registration_endpoint").value("https://money.example.com/oauth/register"))
            .andExpect(jsonPath("$.revocation_endpoint").value("https://money.example.com/oauth/revoke"))
            .andExpect(jsonPath("$.response_types_supported[0]").value("code"))
            .andExpect(jsonPath("$.grant_types_supported[0]").value("authorization_code"))
            .andExpect(jsonPath("$.grant_types_supported[1]").value("refresh_token"))
            .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
            .andExpect(jsonPath("$.token_endpoint_auth_methods_supported[0]").value("none"))
            .andExpect(jsonPath("$.scopes_supported[0]").value("ledger:write"))
    }

    @Test
    fun `an https redirect registers, with defaults`() {
        register(uris("https://claude.ai/api/mcp/auth_callback")).andExpect(status().isCreated)
            .andExpect(jsonPath("$.client_id").isString)
            .andExpect(jsonPath("$.client_name").value("Unnamed assistant"))
            .andExpect(jsonPath("$.redirect_uris[0]").value("https://claude.ai/api/mcp/auth_callback"))
            .andExpect(jsonPath("$.grant_types[0]").value("authorization_code"))
            .andExpect(jsonPath("$.grant_types[1]").value("refresh_token"))
            .andExpect(jsonPath("$.response_types[0]").value("code"))
            .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"))
            .andExpect(jsonPath("$.client_id_issued_at").isNumber)
    }

    @Test
    fun `a client asking for a secret still gets a public registration`() {
        register("""{"client_name":"ChatGPT","redirect_uris":["https://chatgpt.com/cb"],"token_endpoint_auth_method":"client_secret_basic"}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.client_name").value("ChatGPT"))
            .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"))
            .andExpect(jsonPath("$.client_secret").doesNotExist())
    }

    @Test
    fun `loopback http with or without a port registers`() {
        register(uris("http://localhost:33418/callback", "http://127.0.0.1/cb", "http://localhost/cb")).andExpect(status().isCreated)
    }

    @Test
    fun `bad redirect URIs are refused`() {
        for (body in listOf(
            uris("http://example.com/cb"),
            uris("https://claude.ai/cb#frag"),
            uris("http://localhost.evil.com/cb"),
            uris("ftp://claude.ai/cb"),
            uris("not a uri"),
            uris("https:///nohost"),
            uris("https://claude.ai/" + "a".repeat(1000)),
            uris(*Array(11) { "https://claude.ai/cb$it" }),
            uris(),
            """{"client_name":"x"}"""
        )) {
            register(body).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("invalid_redirect_uri"))
                .andExpect(jsonPath("$.error_description").isString)
        }
    }

    @Test
    fun `an overlong client name is refused`() {
        register("""{"client_name":"${"n".repeat(201)}","redirect_uris":["https://claude.ai/cb"]}""")
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("invalid_client_metadata"))
    }

    @Test
    fun `registration is limited to 10 an hour per address`() {
        val before = clients.count()
        repeat(10) { register(uris("https://claude.ai/cb"), address = "10.9.9.9").andExpect(status().isCreated) }
        register(uris("https://claude.ai/cb"), address = "10.9.9.9").andExpect(status().isTooManyRequests)
        register(uris("https://claude.ai/cb"), address = "10.9.9.10").andExpect(status().isCreated)
        org.junit.jupiter.api.Assertions.assertEquals(before + 11, clients.count())
    }
}

/** Without `APP_BASE_URL` there is no OAuth at all, only personal tokens. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:oauth-no-base;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=true",
    "app.base-url="
])
class OAuthWithoutBaseUrlTest {
    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `every OAuth endpoint answers 404`() = assertAllNotFound(mvc)
}

/** MCP off: the OAuth endpoints are not there either. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:oauth-mcp-off;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.mcp.enabled=false",
    "app.base-url=https://money.example.com"
])
class OAuthWhileMcpOffTest {
    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `every OAuth endpoint answers 404`() = assertAllNotFound(mvc)
}

private fun assertAllNotFound(mvc: MockMvc) {
    val requests: List<MockHttpServletRequestBuilder> = listOf(
        get("/.well-known/oauth-protected-resource"),
        get("/.well-known/oauth-protected-resource/mcp"),
        get("/.well-known/oauth-authorization-server"),
        post("/oauth/register").contentType(MediaType.APPLICATION_JSON).content("""{"redirect_uris":["https://claude.ai/cb"]}"""),
        get("/oauth/authorize").param("client_id", "x"),
        post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "refresh_token"),
        post("/oauth/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", "x")
    )
    for (request in requests) mvc.perform(request).andExpect(status().isNotFound)
}
