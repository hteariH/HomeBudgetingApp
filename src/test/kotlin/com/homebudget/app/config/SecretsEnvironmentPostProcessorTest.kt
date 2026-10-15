package com.homebudget.app.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.SpringApplication
import org.springframework.boot.logging.DeferredLogs
import org.springframework.mock.env.MockEnvironment
import java.nio.file.Files
import java.nio.file.Path

class SecretsEnvironmentPostProcessorTest {

    @TempDir lateinit var dataDir: Path

    private fun boot(vararg properties: Pair<String, String>): MockEnvironment {
        val env = MockEnvironment().withProperty("app.data-dir", dataDir.toString())
        properties.forEach { (k, v) -> env.setProperty(k, v) }
        SecretsEnvironmentPostProcessor(DeferredLogs()).postProcessEnvironment(env, SpringApplication())
        return env
    }

    @Test
    fun `generates both secrets when none are configured and remembers them`() {
        val first = boot("app.jwt.secret" to "", "app.crypto.secret" to "")
        val jwt = first.getProperty("app.jwt.secret")!!
        val crypto = first.getProperty("app.crypto.secret")!!
        assertTrue(jwt.length >= 32)
        assertNotEquals(jwt, crypto)
        assertTrue(Files.exists(dataDir.resolve("secrets")))

        val second = boot("app.jwt.secret" to "", "app.crypto.secret" to "")
        assertEquals(jwt, second.getProperty("app.jwt.secret"), "a restart must not sign everyone out")
        assertEquals(crypto, second.getProperty("app.crypto.secret"), "a restart must not lose bank credentials")
    }

    @Test
    fun `a configured value always wins`() {
        val env = boot("app.jwt.secret" to "set-by-the-operator-and-long-enough-for-hs256", "app.crypto.secret" to "")
        assertEquals("set-by-the-operator-and-long-enough-for-hs256", env.getProperty("app.jwt.secret"))
        assertTrue(env.getProperty("app.crypto.secret")!!.isNotBlank())
    }

    @Test
    fun `nothing is written when everything is configured`() {
        boot("app.jwt.secret" to "a".repeat(40), "app.crypto.secret" to "b".repeat(40))
        assertTrue(Files.notExists(dataDir.resolve("secrets")))
    }
}
