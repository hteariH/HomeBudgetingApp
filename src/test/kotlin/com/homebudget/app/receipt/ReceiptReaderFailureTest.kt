package com.homebudget.app.receipt

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.ai.AiProviderSettings
import com.homebudget.app.ai.AiProviderType
import com.homebudget.app.ai.ExtractedReceipt
import com.homebudget.app.ai.ExtractionContext
import com.homebudget.app.ai.ExtractorFactory
import com.homebudget.app.ai.ProviderRequest
import com.homebudget.app.ai.ReceiptExtractor
import com.homebudget.app.ai.ResolvedProvider
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Failures that do not come back as an ExtractionException still end the receipt, with the right reason. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class, ReceiptReaderFailureTest.Scripted::class)
@DirtiesContext
class ReceiptReaderFailureTest {

    companion object {
        private val dataDir: Path = Files.createTempDirectory("receipt-reader-failure")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:h2:mem:receipt-reader-failure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
            }
            registry.add("app.data-dir") { dataDir.toString() }
        }

        @JvmStatic
        @AfterAll
        fun removeDataDir() {
            dataDir.toFile().deleteRecursively()
        }
    }

    /** An extractor that does whatever the test says. */
    class ScriptedExtractors : ExtractorFactory(ObjectMapper()) {
        @Volatile var script: () -> ExtractedReceipt = { error("no script") }

        override fun create(provider: ResolvedProvider, timeout: Duration): ReceiptExtractor = object : ReceiptExtractor {
            override fun extract(jpeg: ByteArray, context: ExtractionContext) = script()
        }
    }

    @TestConfiguration
    class Scripted {
        @Bean
        @Primary
        fun scriptedExtractors() = ScriptedExtractors()
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var settings: AiProviderSettings
    @Autowired private lateinit var reader: ReceiptReader
    @Autowired private lateinit var extractors: ScriptedExtractors

    private lateinit var owner: UserPrincipal

    @BeforeEach
    fun setUp() {
        settings.saveInstance(ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "http://model.test/v1", "vision"))
        owner = identity.newMember("failures")
    }

    @AfterEach
    fun tearDown() = settings.deleteInstance()

    private fun upload(): String {
        val body = mvc.perform(
            multipart("/api/receipts").file(MockMultipartFile("image", "receipt.png", "image/png", pngBytes())).with(asUser(owner))
        ).andExpect(status().isAccepted).andReturn().response.contentAsString
        return ObjectMapper().readTree(body)["uuid"].asText()
    }

    private fun expectFailed(uuid: String, reason: String) {
        mvc.perform(get("/api/receipts/$uuid").with(asUser(owner)))
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureReason").value(reason))
    }

    @Test
    fun `an Error while reading fails the receipt instead of leaving it pending`() {
        extractors.script = { throw StackOverflowError() }

        val uuid = upload()

        assertTrue(reader.awaitIdle(Duration.ofSeconds(20)))
        expectFailed(uuid, "The model's answer could not be read")
    }

    @Test
    fun `a read interrupted by a shutdown is failed as interrupted`() {
        val readers = ArrayBlockingQueue<Thread>(1)
        extractors.script = {
            readers.put(Thread.currentThread())
            Thread.sleep(30_000)
            error("not interrupted")
        }

        val uuid = upload()
        val readingThread = readers.poll(10, TimeUnit.SECONDS)
        assertNotNull(readingThread)
        readingThread!!.interrupt()

        assertTrue(reader.awaitIdle(Duration.ofSeconds(20)))
        expectFailed(uuid, ReceiptReader.INTERRUPTED)
    }

    @Test
    fun `an interrupt that lands after a successful read keeps what was read`() {
        extractors.script = {
            Thread.currentThread().interrupt()
            ExtractedReceipt("SHOP", null, null, BigDecimal("2.00"), emptyList(), null)
        }

        val uuid = upload()

        assertTrue(reader.awaitIdle(Duration.ofSeconds(20)))
        mvc.perform(get("/api/receipts/$uuid").with(asUser(owner)))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.merchant").value("SHOP"))
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `a receipt the reader cannot take is failed rather than left pending`() {
        reader.shutdown()

        val body = mvc.perform(
            multipart("/api/receipts").file(MockMultipartFile("image", "receipt.png", "image/png", pngBytes())).with(asUser(owner))
        ).andExpect(status().isAccepted).andExpect(jsonPath("$.status").value("FAILED")).andReturn().response.contentAsString

        expectFailed(ObjectMapper().readTree(body)["uuid"].asText(), ReceiptReader.INTERRUPTED)
    }
}
