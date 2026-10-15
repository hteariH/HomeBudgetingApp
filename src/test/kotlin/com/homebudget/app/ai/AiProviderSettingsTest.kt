package com.homebudget.app.ai

import com.homebudget.app.TestIdentity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.time.Duration

@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:ai-settings;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
])
class AiProviderSettingsTest {

    @Autowired private lateinit var settings: AiProviderSettings
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val openai = ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-4o", "sk-household")

    @Test
    fun `nothing configured resolves to none`() {
        val household = identity.newMember("none").household
        assertNull(settings.resolveFor(household.id))
        assertNull(settings.instanceProvider())
        assertEquals(AiProviderSource.NONE, settings.viewFor(household.id).source)
    }

    @Test
    fun `the admin page provider is used when nothing else is set, and a household overrides it`() {
        val household = identity.newMember("order").household
        settings.saveInstance(ProviderRequest(AiProviderType.ANTHROPIC, null, "claude-x", "sk-instance"))
        try {
            val instance = settings.resolveFor(household.id)!!
            assertEquals(AiProviderSource.INSTANCE, instance.source)
            assertEquals(AiProviderType.ANTHROPIC, instance.type)
            assertEquals("https://api.anthropic.com/v1/messages", instance.baseUrl)
            assertEquals("sk-instance", instance.apiKey)
            assertNotNull(settings.instanceProvider())

            settings.saveHousehold(household.id, openai)
            val own = settings.resolveFor(household.id)!!
            assertEquals(AiProviderSource.HOUSEHOLD, own.source)
            assertEquals("gpt-4o", own.model)
            assertEquals("sk-household", own.apiKey)

            settings.deleteHousehold(household.id)
            assertEquals(AiProviderSource.INSTANCE, settings.resolveFor(household.id)!!.source)
        } finally {
            settings.deleteInstance()
        }
    }

    @Test
    fun `a household may only use a preset base url for an openai compatible model`() {
        val household = identity.newMember("preset").household
        assertThrows<IllegalArgumentException> {
            settings.saveHousehold(household.id, openai.copy(baseUrl = "http://ollama:11434/v1"))
        }
        assertThrows<IllegalArgumentException> { settings.saveHousehold(household.id, openai.copy(baseUrl = null)) }
        settings.saveHousehold(household.id, openai.copy(baseUrl = "https://openrouter.ai/api/v1"))
        assertEquals("https://openrouter.ai/api/v1", settings.resolveFor(household.id)!!.baseUrl)
    }

    @Test
    fun `the administrator may point at a local model`() {
        try {
            settings.saveInstance(ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "http://ollama:11434/v1", "llava", null))
            val p = settings.instanceProvider()!!
            assertEquals("http://ollama:11434/v1", p.baseUrl)
            assertNull(p.apiKey)
            assertThrows<IllegalArgumentException> {
                settings.saveInstance(ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "not a url", "llava", null))
            }
        } finally {
            settings.deleteInstance()
        }
    }

    @Test
    fun `a null key keeps the stored one and an empty key clears it`() {
        val household = identity.newMember("keys").household
        settings.saveHousehold(household.id, openai)
        settings.saveHousehold(household.id, openai.copy(model = "gpt-4o-mini", apiKey = null))
        val kept = settings.resolveFor(household.id)!!
        assertEquals("gpt-4o-mini", kept.model)
        assertEquals("sk-household", kept.apiKey)
        assertTrue(settings.viewFor(household.id).hasKey)

        settings.saveHousehold(household.id, openai.copy(apiKey = ""))
        assertNull(settings.resolveFor(household.id)!!.apiKey)
        assertFalse(settings.viewFor(household.id).hasKey)
    }

    @Test
    fun `the key is stored encrypted`() {
        val household = identity.newMember("crypt").household
        settings.saveHousehold(household.id, openai)
        val raw = jdbc.queryForObject(
            "SELECT api_key FROM ai_providers WHERE household_id = ?", String::class.java, household.id
        )!!
        assertNotEquals("sk-household", raw)
        assertFalse(raw.contains("sk-household"))
    }

    @Test
    fun `there is only ever one instance provider`() {
        try {
            settings.saveInstance(ProviderRequest(AiProviderType.GEMINI, null, "gemini-a", "k1"))
            settings.saveInstance(ProviderRequest(AiProviderType.GEMINI, null, "gemini-b", "k2"))
            val rows = jdbc.queryForObject("SELECT COUNT(*) FROM ai_providers WHERE scope = 'INSTANCE'", Int::class.java)
            assertEquals(1, rows)
            assertEquals("gemini-b", settings.instanceProvider()!!.model)
        } finally {
            settings.deleteInstance()
        }
    }

    @Test
    fun `the model name, key and address are checked before they are stored`() {
        val household = identity.newMember("limits").household
        for (bad in listOf("gpt 4o", "a?b", "x#y", "m\tx", "é", "a".repeat(201))) {
            assertThrows<IllegalArgumentException>(bad) { settings.saveHousehold(household.id, openai.copy(model = bad)) }
        }
        settings.saveHousehold(household.id, openai.copy(model = "openai/gpt-4o-mini:free@v1.2_x"))
        settings.saveHousehold(household.id, openai.copy(model = "m".repeat(200)))

        assertThrows<IllegalArgumentException> {
            settings.saveHousehold(household.id, openai.copy(apiKey = "k".repeat(AiProviderSettings.MAX_KEY_LENGTH + 1)))
        }
        // The longest key allowed must still fit the column once encrypted.
        settings.saveHousehold(household.id, openai.copy(apiKey = "k".repeat(AiProviderSettings.MAX_KEY_LENGTH)))
        assertEquals(AiProviderSettings.MAX_KEY_LENGTH, settings.resolveFor(household.id)!!.apiKey!!.length)

        try {
            assertThrows<IllegalArgumentException> {
                settings.saveInstance(ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "http://h/" + "p".repeat(500), "m", null))
            }
        } finally {
            settings.deleteInstance()
        }
    }

    @Test
    fun `keys are trimmed and a key with other characters is refused without quoting it`() {
        val household = identity.newMember("keychars").household
        settings.saveHousehold(household.id, openai.copy(apiKey = "  sk-trimmed\n"))
        assertEquals("sk-trimmed", settings.resolveFor(household.id)!!.apiKey)
        assertEquals("sk-x", settings.unsavedHousehold(household.id, openai.copy(apiKey = " sk-x ")).apiKey)

        for (bad in listOf("sk-leak\nme", "sk-leak me", "sk-leak\u00e9", "sk-leak\u0000", "sk-leak\u007f", "sk-leak\r\nX: y")) {
            val e = assertThrows<IllegalArgumentException>(bad) { settings.saveHousehold(household.id, openai.copy(apiKey = bad)) }
            assertEquals(AiProviderSettings.KEY_CHARACTERS_NOT_ALLOWED, e.message)
            assertThrows<IllegalArgumentException>(bad) { settings.unsavedHousehold(household.id, openai.copy(apiKey = bad)) }
        }
        assertEquals("sk-trimmed", settings.resolveFor(household.id)!!.apiKey)
    }

    @Test
    fun `a key is not carried to a new address`() {
        try {
            settings.saveInstance(ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "https://llm.example.com/v1", "m", "sk-a"))
            // Same address, written differently: the key stays.
            settings.saveInstance(ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, " https://llm.example.com/v1/ ", "m2", null))
            assertEquals("sk-a", settings.instanceProvider()!!.apiKey)

            settings.saveInstance(ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "https://other.example.com/v1", "m2", null))
            assertNull(settings.instanceProvider()!!.apiKey)
        } finally {
            settings.deleteInstance()
        }

        val household = identity.newMember("move").household
        settings.saveHousehold(household.id, openai)
        settings.saveHousehold(household.id, openai.copy(baseUrl = "https://openrouter.ai/api/v1", apiKey = null))
        assertNull(settings.resolveFor(household.id)!!.apiKey)
    }

    @Test
    fun `unsaved settings resolve with the stored key only for the same address`() {
        val household = identity.newMember("unsaved").household
        settings.saveHousehold(household.id, openai)

        val same = settings.unsavedHousehold(household.id, openai.copy(model = "gpt-4o-mini", apiKey = null))
        assertEquals("sk-household", same.apiKey)
        assertEquals("gpt-4o-mini", same.model)
        assertEquals(AiProviderSource.HOUSEHOLD, same.source)

        assertNull(settings.unsavedHousehold(household.id, openai.copy(baseUrl = "https://openrouter.ai/api/v1", apiKey = null)).apiKey)
        assertNull(settings.unsavedHousehold(household.id, ProviderRequest(AiProviderType.ANTHROPIC, null, "c", null)).apiKey)
        assertEquals("new", settings.unsavedHousehold(household.id, openai.copy(apiKey = "new")).apiKey)
        assertThrows<IllegalArgumentException> {
            settings.unsavedHousehold(household.id, openai.copy(baseUrl = "http://10.0.0.5/v1"))
        }
        // Nothing was saved by asking.
        assertEquals("gpt-4o", settings.resolveFor(household.id)!!.model)

        val gemini = settings.unsavedInstance(ProviderRequest(AiProviderType.GEMINI, null, "gemini-x", "g"))
        assertEquals(AiProviderSettings.GEMINI_URL, gemini.baseUrl)
        assertNull(settings.instanceProvider())
    }

    @Test
    fun `a provider request never prints its key`() {
        val text = openai.toString()
        assertFalse(text.contains("sk-household"), text)
        assertTrue(text.contains("gpt-4o"), text)
    }

    @Test
    fun `timeout and concurrency have defaults`() {
        assertEquals(Duration.ofMinutes(5), settings.timeout)
        assertEquals(2, settings.concurrency)
    }
}

@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:ai-settings-env;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.ai.provider=ANTHROPIC",
    "app.ai.model=claude-env",
    "app.ai.api-key=sk-env",
    "app.ai.timeout=90s",
    "app.ai.concurrency=4"
])
class AiProviderSettingsEnvironmentTest {

    @Autowired private lateinit var settings: AiProviderSettings
    @Autowired private lateinit var identity: TestIdentity

    @Test
    fun `the environment beats the admin page and loses to a household`() {
        val household = identity.newMember("env").household
        assertTrue(settings.lockedByEnvironment)
        assertEquals(AiProviderSource.ENVIRONMENT, settings.resolveFor(household.id)!!.source)
        assertEquals("claude-env", settings.instanceProvider()!!.model)
        assertEquals(Duration.ofSeconds(90), settings.timeout)
        assertEquals(4, settings.concurrency)

        settings.saveHousehold(
            household.id, ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-h", "k")
        )
        assertEquals(AiProviderSource.HOUSEHOLD, settings.resolveFor(household.id)!!.source)
    }

    @Test
    fun `the administrator cannot change a provider the environment sets`() {
        assertThrows<AiProviderLockedException> {
            settings.saveInstance(ProviderRequest(AiProviderType.GEMINI, null, "g", null))
        }
        assertThrows<AiProviderLockedException> { settings.deleteInstance() }
        assertThrows<AiProviderLockedException> {
            settings.unsavedInstance(ProviderRequest(AiProviderType.GEMINI, null, "g", null))
        }
    }
}

/** The environment's key is read once, at startup; a bad one is dropped rather than sent. */
class AiProviderEnvironmentKeyTest {

    private fun settingsWithKey(key: String) = AiProviderSettings(
        org.mockito.Mockito.mock(AiProviderRepository::class.java),
        "ANTHROPIC", "", "claude-env", key, Duration.ofMinutes(5), 2
    )

    @Test
    fun `the environment key is trimmed`() {
        assertEquals("sk-env", settingsWithKey("  sk-env\n").instanceProvider()!!.apiKey)
    }

    @Test
    fun `an environment key with characters that are not allowed is treated as absent`() {
        val provider = settingsWithKey("sk-env\nX: y").instanceProvider()!!
        assertEquals(AiProviderSource.ENVIRONMENT, provider.source)
        assertNull(provider.apiKey)
        assertNull(settingsWithKey("sk-\u00e9").instanceProvider()!!.apiKey)
    }
}
