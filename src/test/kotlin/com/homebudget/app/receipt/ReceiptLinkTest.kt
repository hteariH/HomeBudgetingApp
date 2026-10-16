package com.homebudget.app.receipt

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.SyncAccount
import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.dto.SyncPushRequest
import com.homebudget.app.dto.SyncTransaction
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.AccountService
import com.homebudget.app.service.SyncService
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** A receipt saved with a transaction - over REST and over sync - and deleted with it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestIdentity::class)
@DirtiesContext
class ReceiptLinkTest {

    companion object {
        private val dataDir: Path = Files.createTempDirectory("receipt-link")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:h2:mem:receipt-link;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
            }
            registry.add("app.data-dir") { dataDir.toString() }
            registry.add("spring.jpa.properties.hibernate.generate_statistics") { "true" }
        }

        @JvmStatic
        @AfterAll
        fun removeDataDir() {
            dataDir.toFile().deleteRecursively()
        }
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var receipts: ReceiptRepository
    @Autowired private lateinit var images: ReceiptImages
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var entityManagerFactory: EntityManagerFactory

    private val json = ObjectMapper()
    private lateinit var owner: UserPrincipal
    private lateinit var accountUuid: String
    private var accountId: Long = 0

    @BeforeEach
    fun setUp() {
        owner = identity.newMember("linker")
        identity.signIn(owner)
        accountUuid = UUID.randomUUID().toString()
        syncService.push(SyncPushRequest(SyncPayload(accounts = listOf(SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD")))))
        accountId = accountService.findAll().single { it.uuid == accountUuid }.id
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun newReceipt(p: UserPrincipal = owner): Receipt {
        val uuid = UUID.randomUUID().toString()
        val path = images.store(pngBytes(40, 30), p.household.uuid, uuid)
        return receipts.save(Receipt(uuid = uuid, household = p.household, status = ReceiptStatus.READY, imagePath = path))
    }

    private fun gone(receipt: Receipt) =
        receipts.findById(receipt.id).isEmpty && !Files.exists(images.resolve(receipt.imagePath))

    private fun createBody(receiptUuid: String?) = json.writeValueAsString(
        mapOf(
            "accountId" to accountId, "amount" to "2.00", "type" to "EXPENSE", "date" to "2026-10-14",
            "receiptUuid" to receiptUuid
        )
    )

    private fun createOverRest(receiptUuid: String?) =
        mvc.perform(post("/api/transactions").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON).content(createBody(receiptUuid)))

    private fun syncTx(uuid: String, receiptUuid: String?, deleted: Boolean = false, base: Instant? = null) = SyncTransaction(
        uuid = uuid,
        baseUpdatedAt = base,
        deleted = deleted,
        accountUuid = accountUuid,
        amount = BigDecimal("2.00"),
        currencyCode = "USD",
        accountAmount = BigDecimal("2.00"),
        type = TransactionType.EXPENSE,
        occurredAt = Instant.parse("2026-10-14T10:00:00Z"),
        receiptUuid = receiptUuid
    )

    private fun push(vararg tx: SyncTransaction) = syncService.push(SyncPushRequest(SyncPayload(transactions = tx.toList())))

    @Test
    fun `a transaction created over REST keeps its receipt, and deleting it deletes the receipt`() {
        val receipt = newReceipt()
        val body = createOverRest(receipt.uuid)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.receiptUuid").value(receipt.uuid))
            .andReturn().response.contentAsString
        val id = json.readTree(body)["id"].asLong()

        // One receipt, one transaction.
        createOverRest(receipt.uuid).andExpect(status().isBadRequest)

        mvc.perform(delete("/api/transactions/$id").with(asUser(owner))).andExpect(status().is2xxSuccessful)
        assertTrue(gone(receipt), "the receipt row and its photo are deleted with the transaction")
    }

    @Test
    fun `a receipt is attached by an update and a foreign one is not found`() {
        val created = createOverRest(null).andExpect(status().isCreated)
            .andExpect(jsonPath("$.receiptUuid").doesNotExist())
            .andReturn().response.contentAsString
        val id = json.readTree(created)["id"].asLong()
        val receipt = newReceipt()

        mvc.perform(put("/api/transactions/$id").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("""{"receiptUuid":"${receipt.uuid}"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receiptUuid").value(receipt.uuid))
        // Saving the same transaction with the same receipt again is not a conflict.
        mvc.perform(put("/api/transactions/$id").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("""{"receiptUuid":"${receipt.uuid}","description":"milk"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.receiptUuid").value(receipt.uuid))

        val foreign = newReceipt(identity.newMember("neighbour"))
        createOverRest(foreign.uuid).andExpect(status().isNotFound)
        mvc.perform(put("/api/transactions/$id").with(asUser(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("""{"receiptUuid":"${foreign.uuid}"}"""))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `sync links a pushed transaction to its receipt and pulls it back`() {
        val receipt = newReceipt()
        val txUuid = UUID.randomUUID().toString()

        val pushed = push(syncTx(txUuid, receipt.uuid))
        assertTrue(pushed.rejected.isEmpty(), "unexpected rejections: ${pushed.rejected}")

        val pulled = syncService.pull(Instant.EPOCH, 500).changes.transactions.single { it.uuid == txUuid }
        assertEquals(receipt.uuid, pulled.receiptUuid)

        // A client that does not know about receipts pushes without one; the link stays.
        assertTrue(push(syncTx(txUuid, null, base = pulled.updatedAt)).rejected.isEmpty())
        val again = syncService.pull(Instant.EPOCH, 500).changes.transactions.single { it.uuid == txUuid }
        assertEquals(receipt.uuid, again.receiptUuid)

        val deleted = push(syncTx(txUuid, receipt.uuid, deleted = true, base = again.updatedAt))
        assertTrue(deleted.rejected.isEmpty(), "unexpected rejections: ${deleted.rejected}")
        assertTrue(gone(receipt), "a tombstone pushed by sync deletes the receipt and its photo")
        val tombstone = syncService.pull(Instant.EPOCH, 500).changes.transactions.single { it.uuid == txUuid }
        assertTrue(tombstone.deleted)
    }

    @Test
    fun `one push naming a receipt on two new transactions links the first and saves the second without it`() {
        val receipt = newReceipt()
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()

        val pushed = push(syncTx(first, receipt.uuid), syncTx(second, receipt.uuid))

        assertEquals(2, pushed.applied, "rejections: ${pushed.rejected}")
        assertTrue(pushed.rejected.isEmpty())
        val pulled = syncService.pull(Instant.EPOCH, 500).changes.transactions
        assertEquals(receipt.uuid, pulled.single { it.uuid == first }.receiptUuid)
        assertNull(pulled.single { it.uuid == second }.receiptUuid)
    }

    @Test
    fun `one push attaching a receipt to two existing transactions links only the first`() {
        val receipt = newReceipt()
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        val created = push(syncTx(first, null), syncTx(second, null))
        assertTrue(created.rejected.isEmpty())

        // The stored stamps, as a client would have them from its last pull.
        val stored = syncService.pull(Instant.EPOCH, 500).changes.transactions.associate { it.uuid to it.updatedAt }
        val pushed = push(
            syncTx(first, receipt.uuid, base = stored[first]),
            syncTx(second, receipt.uuid, base = stored[second])
        )

        assertEquals(2, pushed.applied, "rejections: ${pushed.rejected}")
        val pulled = syncService.pull(Instant.EPOCH, 500).changes.transactions
        assertEquals(receipt.uuid, pulled.single { it.uuid == first }.receiptUuid)
        assertNull(pulled.single { it.uuid == second }.receiptUuid)
    }

    @Test
    fun `sync saves an entry naming a foreign or taken receipt without linking it`() {
        val foreign = newReceipt(identity.newMember("neighbour"))
        identity.signIn(owner)
        val foreignTx = UUID.randomUUID().toString()
        val withForeign = push(syncTx(foreignTx, foreign.uuid))
        assertTrue(withForeign.rejected.isEmpty(), "rejections: ${withForeign.rejected}")

        val receipt = newReceipt()
        val holder = UUID.randomUUID().toString()
        assertTrue(push(syncTx(holder, receipt.uuid)).rejected.isEmpty())
        val second = UUID.randomUUID().toString()
        assertTrue(push(syncTx(second, receipt.uuid)).rejected.isEmpty())

        val pulled = syncService.pull(Instant.EPOCH, 500).changes.transactions
        assertNull(pulled.single { it.uuid == foreignTx }.receiptUuid)
        assertEquals(receipt.uuid, pulled.single { it.uuid == holder }.receiptUuid)
        assertNull(pulled.single { it.uuid == second }.receiptUuid)
        assertFalse(gone(receipt))
        assertFalse(gone(foreign))
    }

    @Test
    fun `sync saves a new entry naming an unknown receipt without a link`() {
        val txUuid = UUID.randomUUID().toString()

        val pushed = push(syncTx(txUuid, UUID.randomUUID().toString()))

        assertEquals(1, pushed.applied, "rejections: ${pushed.rejected}")
        assertNull(syncService.pull(Instant.EPOCH, 500).changes.transactions.single { it.uuid == txUuid }.receiptUuid)
    }

    @Test
    fun `lists and pulls name each entry's receipt without loading the receipts`() {
        val linked = List(3) { UUID.randomUUID().toString() to newReceipt() }
        assertTrue(push(*linked.map { (tx, r) -> syncTx(tx, r.uuid) }.toTypedArray()).rejected.isEmpty())
        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.clear()

        val pulled = syncService.pull(Instant.EPOCH, 500).changes.transactions.associate { it.uuid to it.receiptUuid }
        val listed = json.readTree(
            mvc.perform(get("/api/transactions?size=100").with(asUser(owner))).andExpect(status().isOk)
                .andReturn().response.contentAsString
        )["content"].associate { it["uuid"].asText() to it["receiptUuid"]?.takeUnless { n -> n.isNull }?.asText() }

        linked.forEach { (tx, r) ->
            assertEquals(r.uuid, pulled[tx])
            assertEquals(r.uuid, listed[tx])
        }
        assertEquals(0, statistics.getEntityStatistics(Receipt::class.java.name).loadCount, "no receipt row is loaded")
    }
}
