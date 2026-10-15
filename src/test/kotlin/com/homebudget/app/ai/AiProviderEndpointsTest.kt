package com.homebudget.app.ai

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Role
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    @Autowired private lateinit var providers: AiProviderRepository
    @Autowired private lateinit var sample: SampleReceiptTest

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

    private fun openAiReply(text: String) =
        """{"choices":[{"message":{"role":"assistant","content":${com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(text)}}}]}"""

    @Test
    fun `the administrator tests unsaved settings with the sample receipt`() {
        val admin = admin()
        FakeModelServer().use { server ->
            server.reply(200, openAiReply("""{"merchant":"SHOP","date":"2026-10-14","total":"2.00","items":[{"name":"Milk","price":"1.20"}]}"""))
            mvc.perform(
                post("/api/admin/ai-provider/test").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"OPENAI_COMPATIBLE","baseUrl":"${server.base}/v1","model":"llava","apiKey":"sk-t"}""")
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.receipt.merchant").value("SHOP"))
                .andExpect(jsonPath("$.receipt.date").value("2026-10-14"))
                .andExpect(jsonPath("$.receipt.items[0].name").value("Milk"))
                .andExpect(jsonPath("$.reason").doesNotExist())

            val sent = server.requests.single()
            assertEquals("Bearer sk-t", sent.headers["authorization"])
            val sample = javaClass.getResourceAsStream("/ai/sample-receipt.jpg")!!.readBytes()
            assertTrue(sent.body.contains(java.util.Base64.getEncoder().encodeToString(sample)), "the sample image is sent")

            server.reply(400, """{"error":"model does not support image input"}""")
            mvc.perform(
                post("/api/admin/ai-provider/test").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"OPENAI_COMPATIBLE","baseUrl":"${server.base}/v1","model":"text-only"}""")
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.reason").value("This model cannot read images"))
                .andExpect(jsonPath("$.receipt").doesNotExist())
        }
        // Testing saves nothing.
        mvc.perform(get("/api/admin/ai-provider").with(asUser(admin))).andExpect(jsonPath("$.source").value("NONE"))
    }

    @Test
    fun `an empty test request tests the saved provider`() {
        val admin = admin()
        mvc.perform(post("/api/admin/ai-provider/test").with(asUser(admin)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(false))
            .andExpect(jsonPath("$.reason").value("No model is set up to read receipts"))

        FakeModelServer().use { server ->
            mvc.perform(
                put("/api/admin/ai-provider").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"OPENAI_COMPATIBLE","baseUrl":"${server.base}/v1","model":"llava","apiKey":"sk-saved"}""")
            ).andExpect(status().isOk)
            try {
                server.reply(200, openAiReply("""{"merchant":"SHOP","items":[]}"""))
                mvc.perform(post("/api/admin/ai-provider/test").with(asUser(admin)))
                    .andExpect(status().isOk).andExpect(jsonPath("$.ok").value(true))
                assertEquals("Bearer sk-saved", server.requests.single().headers["authorization"])
            } finally {
                mvc.perform(delete("/api/admin/ai-provider").with(asUser(admin)))
            }
        }
    }

    @Test
    fun `a stored key the JDK refuses never comes back from the Test button`() {
        val admin = admin()
        FakeModelServer().use { server ->
            // Stored past validation, as an older version or a hand-edited row could have it.
            val row = providers.save(AiProviderEntity(scope = AiProviderScope.INSTANCE, type = AiProviderType.OPENAI_COMPATIBLE,
                baseUrl = "${server.base}/v1", model = "llava", apiKey = "sk-leak\nme"))
            try {
                val answer = mvc.perform(post("/api/admin/ai-provider/test").with(asUser(admin)))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(false))
                    .andReturn().response.contentAsString
                assertFalse(answer.contains("sk-leak"), answer)
                assertTrue(server.requests.isEmpty())
            } finally {
                providers.delete(row)
            }
        }
    }

    @Test
    fun `a key with characters that are not allowed is refused without quoting it`() {
        val owner = identity.newMember("badkey")
        val request = """{"type":"OPENAI_COMPATIBLE","baseUrl":"https://api.openai.com/v1","model":"m","apiKey":"sk-leak\nme"}"""
        val answer = mvc.perform(
            put("/api/household/ai-provider").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON).content(request)
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("The API key contains characters that are not allowed"))
            .andReturn().response.contentAsString
        assertFalse(answer.contains("sk-leak"), answer)
        mvc.perform(
            post("/api/household/ai-provider/test").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON).content(request)
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `a household test without a body never spends the instance provider`() {
        val admin = admin()
        val owner = identity.newMember("frugal")
        FakeModelServer().use { server ->
            mvc.perform(
                put("/api/admin/ai-provider").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"OPENAI_COMPATIBLE","baseUrl":"${server.base}/v1","model":"llava"}""")
            ).andExpect(status().isOk)
            try {
                mvc.perform(post("/api/household/ai-provider/test").with(asUser(owner)))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.ok").value(false))
                    .andExpect(jsonPath("$.reason").value("Set up a household provider first"))
                assertTrue(server.requests.isEmpty())
            } finally {
                mvc.perform(delete("/api/admin/ai-provider").with(asUser(admin)))
            }
        }
    }

    @Test
    fun `tests beyond AI_CONCURRENCY wait briefly and are then refused with 429`() {
        val admin = admin()
        val wait = sample.slotWait
        sample.slotWait = java.time.Duration.ofMillis(200)
        FakeModelServer().use { server ->
            val request = """{"type":"OPENAI_COMPATIBLE","baseUrl":"${server.base}/v1","model":"llava"}"""
            fun test() = mvc.perform(
                post("/api/admin/ai-provider/test").with(asUser(admin)).contentType(MediaType.APPLICATION_JSON).content(request)
            )
            repeat(2) { server.reply(200, openAiReply("""{"merchant":"SHOP","items":[]}"""), delayMillis = 3_000) }
            val running = List(2) { Thread { test() }.apply { start() } }
            try {
                val deadline = System.nanoTime() + 5_000_000_000
                while (server.requests.size < 2 && System.nanoTime() < deadline) Thread.sleep(10)
                assertEquals(2, server.requests.size)

                test().andExpect(status().isTooManyRequests)
                    .andExpect(jsonPath("$.message").value("The model is busy, try again in a minute"))
                assertEquals(2, server.requests.size)
            } finally {
                running.forEach { it.join(10_000) }
                sample.slotWait = wait
            }
        }
    }

    @Test
    fun `household tests follow the household rules`() {
        val owner = identity.newMember("tester")
        val member = identity.joinAs("member", owner.household)
        mvc.perform(post("/api/household/ai-provider/test").with(asUser(member))).andExpect(status().isForbidden)
        mvc.perform(post("/api/admin/ai-provider/test").with(asUser(owner))).andExpect(status().isForbidden)
        mvc.perform(
            post("/api/household/ai-provider/test").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"OPENAI_COMPATIBLE","baseUrl":"http://10.0.0.5/v1","model":"m"}""")
        ).andExpect(status().isBadRequest)
        mvc.perform(post("/api/household/ai-provider/test").with(asUser(owner)))
            .andExpect(status().isOk).andExpect(jsonPath("$.ok").value(false))
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
