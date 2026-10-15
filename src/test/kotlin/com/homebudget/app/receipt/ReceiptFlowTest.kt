package com.homebudget.app.receipt

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.ai.AiProviderSettings
import com.homebudget.app.ai.AiProviderType
import com.homebudget.app.ai.FakeModelServer
import com.homebudget.app.ai.ProviderRequest
import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.util.Base64
import javax.imageio.ImageIO

internal fun asUser(p: UserPrincipal) = authentication(UsernamePasswordAuthenticationToken(p, null, p.authorities))

internal fun pngBytes(width: Int = 800, height: Int = 600): ByteArray =
    ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }
        .toByteArray()

/** Upload, read in the background by a fake OpenAI-compatible model, poll, fetch the photo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@DirtiesContext
class ReceiptFlowTest {

    companion object {
        private val dataDir: Path = Files.createTempDirectory("receipt-flow")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:h2:mem:receipt-flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
            }
            registry.add("app.data-dir") { dataDir.toString() }
            registry.add("app.ai.timeout") { "10s" }
        }

        @JvmStatic
        @AfterAll
        fun removeDataDir() {
            dataDir.toFile().deleteRecursively()
        }
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var settings: AiProviderSettings
    @Autowired private lateinit var reader: ReceiptReader
    @Autowired private lateinit var receipts: ReceiptRepository
    @Autowired private lateinit var images: ReceiptImages
    @Autowired private lateinit var categories: CategoryRepository

    private val server = FakeModelServer()
    private lateinit var owner: UserPrincipal

    @BeforeEach
    fun setUp() {
        settings.saveInstance(ProviderRequest(AiProviderType.OPENAI_COMPATIBLE, "${server.base}/v1", "vision"))
        owner = identity.newMember("receipts")
    }

    @AfterEach
    fun tearDown() {
        server.close()
        settings.deleteInstance()
    }

    private fun openAiReply(text: String) =
        """{"choices":[{"message":{"role":"assistant","content":${ObjectMapper().writeValueAsString(text)}}}]}"""

    private fun upload(principal: UserPrincipal, bytes: ByteArray = pngBytes()) =
        mvc.perform(multipart("/api/receipts").file(MockMultipartFile("image", "receipt.png", "image/png", bytes)).with(asUser(principal)))

    private fun uploadAndRead(principal: UserPrincipal = owner): String {
        val body = upload(principal)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().response.contentAsString
        assertTrue(reader.awaitIdle(Duration.ofSeconds(20)), "the receipt was read")
        return ObjectMapper().readTree(body)["uuid"].asText()
    }

    @Test
    fun `an upload is read in the background and its photo can be fetched`() {
        val groceries = categories.save(Category(name = "Groceries", household = owner.household, type = CategoryType.EXPENSE))
        server.reply(200, openAiReply(
            """{"merchant":"SHOP","date":"2026-10-14","currency":"eur","total":"2.00",""" +
                """"items":[{"name":"Milk","quantity":"1","price":"1.20"},{"name":"Bread","price":"0.80"}],""" +
                """"suggestedCategory":"groceries"}"""
        ))

        val uuid = uploadAndRead()

        mvc.perform(get("/api/receipts/$uuid").with(asUser(owner)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.merchant").value("SHOP"))
            .andExpect(jsonPath("$.date").value("2026-10-14"))
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.total").value("2.00"))
            .andExpect(jsonPath("$.totalMismatch").value(false))
            .andExpect(jsonPath("$.suggestedCategoryId").value(groceries.id))
            .andExpect(jsonPath("$.suggestedCategoryUuid").value(groceries.uuid))
            .andExpect(jsonPath("$.items[0].name").value("Milk"))
            .andExpect(jsonPath("$.items[0].quantity").value("1"))
            .andExpect(jsonPath("$.items[0].price").value("1.20"))
            .andExpect(jsonPath("$.items[1].name").value("Bread"))
            .andExpect(jsonPath("$.items[1].quantity").doesNotExist())
            .andExpect(jsonPath("$.failureReason").doesNotExist())

        val stored = receipts.findByUuidAndHouseholdId(uuid, owner.household.id)!!
        assertEquals(200L, stored.totalMinor, "the total is kept in minor units")
        assertEquals(LocalDate.parse("2026-10-14"), stored.receiptDate)
        assertEquals("receipts/${owner.household.uuid}/$uuid.jpg", stored.imagePath)

        val photo = images.read(stored.imagePath)!!
        val sent = server.requests.single().body
        assertTrue(sent.contains(Base64.getEncoder().encodeToString(photo)), "the model got the stored JPEG")
        assertTrue(sent.contains("Groceries"), "the model was offered the household's categories")

        val fetched = mvc.perform(get("/api/receipts/$uuid/image").with(asUser(owner)))
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/jpeg"))
            .andReturn().response.contentAsByteArray
        assertArrayEquals(photo, fetched)

        val stranger = identity.newMember("stranger")
        mvc.perform(get("/api/receipts/$uuid").with(asUser(stranger))).andExpect(status().isNotFound)
        mvc.perform(get("/api/receipts/$uuid/image").with(asUser(stranger))).andExpect(status().isNotFound)
    }

    @Test
    fun `what does not check out is dropped or flagged`() {
        // Household base currency is USD; XYZ is not in the catalogue.
        server.reply(200, openAiReply(
            """{"merchant":"KIOSK","currency":"XYZ","total":"5.00",""" +
                """"items":[{"name":"Milk","price":"1.20"},{"name":"Bread","price":"0.80"}],"suggestedCategory":"Spaceships"}"""
        ))

        val uuid = uploadAndRead()

        mvc.perform(get("/api/receipts/$uuid").with(asUser(owner)))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.currency").doesNotExist())
            .andExpect(jsonPath("$.total").value("5.00"))
            .andExpect(jsonPath("$.totalMismatch").value(true))
            .andExpect(jsonPath("$.suggestedCategoryId").doesNotExist())
            .andExpect(jsonPath("$.suggestedCategoryUuid").doesNotExist())
    }

    @Test
    fun `a model answering garbage twice fails the receipt with the exact reason`() {
        server.reply(200, openAiReply("I am not sure what this is."))
        server.reply(200, openAiReply("Still not JSON, sorry."))

        val uuid = uploadAndRead()

        mvc.perform(get("/api/receipts/$uuid").with(asUser(owner)))
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureReason").value("The model's answer could not be read"))
            .andExpect(jsonPath("$.merchant").doesNotExist())
        assertEquals(2, server.requests.size)
    }

    @Test
    fun `without a model an upload is refused and nothing is kept`() {
        settings.deleteInstance()
        val before = receipts.count()

        upload(owner)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Receipt recognition is not set up"))
        assertEquals(before, receipts.count())
    }

    @Test
    fun `too large and unreadable uploads are refused`() {
        upload(owner, ByteArray(16 * 1024 * 1024))
            .andExpect(status().isPayloadTooLarge)

        val gif = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "gif", it) }
        upload(owner, gif.toByteArray())
            .andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.message").value("Send the photo as JPEG or PNG"))

        assertTrue(server.requests.isEmpty(), "nothing reached the model")
    }
}
