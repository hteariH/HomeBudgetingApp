package com.homebudget.app.ai

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Role
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:ai-endpoints;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
])
class AiProviderEndpointsTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var users: UserRepository

    private fun admin(): UserPrincipal {
        val made = identity.newMember("admin")
        users.save(made.user.apply { role = Role.ADMIN })
        return UserPrincipal(made.user, made.household)
    }

    private val body = """{"type":"OPENAI_COMPATIBLE","baseUrl":"https://api.openai.com/v1","model":"gpt-4o","apiKey":"sk-secret-123"}"""

    @Test
    fun `an owner sets a household provider and the key never comes back`() {
        val owner = identity.newMember("owner")
        mvc.perform(get("/api/household/ai-provider").with(asUser(owner)))
            .andExpect(status().isOk).andExpect(jsonPath("$.source").value("NONE"))

        mvc.perform(put("/api/household/ai-provider").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source").value("HOUSEHOLD"))
            .andExpect(jsonPath("$.hasKey").value(true))
            .andExpect(jsonPath("$.host").value("api.openai.com"))
            .andExpect(jsonPath("$.apiKey").doesNotExist())

        val got = mvc.perform(get("/api/household/ai-provider").with(asUser(owner)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        check(!got.contains("sk-secret-123")) { "key leaked: $got" }

        mvc.perform(delete("/api/household/ai-provider").with(asUser(owner))).andExpect(status().isNoContent)
        mvc.perform(get("/api/household/ai-provider").with(asUser(owner)))
            .andExpect(jsonPath("$.source").value("NONE"))
    }

    @Test
    fun `a member may read but not change the household provider`() {
        val owner = identity.newMember("owner")
        val member = identity.joinAs("member", owner.household)
        mvc.perform(get("/api/household/ai-provider").with(asUser(member))).andExpect(status().isOk)
        mvc.perform(put("/api/household/ai-provider").with(asUser(member)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        mvc.perform(delete("/api/household/ai-provider").with(asUser(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `a household cannot point at an arbitrary url`() {
        val owner = identity.newMember("owner")
        mvc.perform(
            put("/api/household/ai-provider").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"OPENAI_COMPATIBLE","baseUrl":"http://10.0.0.5/v1","model":"m"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `only the administrator reaches the admin provider and the instance reports receipt scanning`() {
        val member = identity.newMember("plain")
        mvc.perform(get("/api/admin/ai-provider").with(asUser(member))).andExpect(status().isForbidden)
        mvc.perform(put("/api/admin/ai-provider").with(asUser(member)).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)

        val admin = admin()
        mvc.perform(get("/api/instance")).andExpect(jsonPath("$.receiptScan").value(false))
        mvc.perform(
            put("/api/admin/ai-provider").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"OPENAI_COMPATIBLE","baseUrl":"http://ollama:11434/v1","model":"llava"}""")
        ).andExpect(status().isOk).andExpect(jsonPath("$.source").value("INSTANCE")).andExpect(jsonPath("$.hasKey").value(false))
        mvc.perform(get("/api/instance")).andExpect(jsonPath("$.receiptScan").value(true))
        mvc.perform(delete("/api/admin/ai-provider").with(asUser(admin))).andExpect(status().isNoContent)
        mvc.perform(get("/api/instance")).andExpect(jsonPath("$.receiptScan").value(false))
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:ai-endpoints-env;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.ai.provider=GEMINI",
    "app.ai.model=gemini-env",
    "app.ai.api-key=env-key"
])
class AiProviderEnvironmentEndpointsTest {

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var users: UserRepository

    @Test
    fun `the admin sees the environment provider but cannot change it`() {
        val made = identity.newMember("admin")
        users.save(made.user.apply { role = Role.ADMIN })
        val admin = UserPrincipal(made.user, made.household)

        mvc.perform(get("/api/admin/ai-provider").with(asUser(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source").value("ENVIRONMENT"))
            .andExpect(jsonPath("$.hasKey").value(true))
        mvc.perform(
            put("/api/admin/ai-provider").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"GEMINI","model":"x"}""")
        ).andExpect(status().isConflict)
        mvc.perform(delete("/api/admin/ai-provider").with(asUser(admin))).andExpect(status().isConflict)
        mvc.perform(get("/api/instance")).andExpect(jsonPath("$.receiptScan").value(true))
    }
}
