package com.homebudget.app.receipt

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Account
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
@DirtiesContext
class ReceiptCleanupTest {

    companion object {
        private val dataDir: Path = Files.createTempDirectory("receipt-cleanup")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:h2:mem:receipt-cleanup;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
            }
            registry.add("app.data-dir") { dataDir.toString() }
        }

        @JvmStatic
        @AfterAll
        fun removeDataDir() {
            dataDir.toFile().deleteRecursively()
        }
    }

    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var receipts: ReceiptRepository
    @Autowired private lateinit var images: ReceiptImages
    @Autowired private lateinit var cleanup: ReceiptCleanup
    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var currencies: CurrencyRepository
    @Autowired private lateinit var transactions: TransactionRepository

    private fun receipt(p: UserPrincipal, age: Duration): Receipt {
        val uuid = UUID.randomUUID().toString()
        val path = images.store(pngBytes(40, 30), p.household.uuid, uuid)
        return receipts.save(
            Receipt(uuid = uuid, household = p.household, status = ReceiptStatus.READY, imagePath = path,
                createdAt = Instant.now().minus(age))
        )
    }

    private fun saveWith(p: UserPrincipal, receipt: Receipt, deleted: Boolean = false) {
        val account = accounts.save(Account(name = "Cash", household = p.household, currency = currencies.findByCode("USD")!!))
        transactions.save(
            Transaction(household = p.household, account = account, amountMinor = 100, currencyCode = "USD",
                accountAmountMinor = 100, type = TransactionType.EXPENSE, occurredAt = Instant.now(),
                receipt = receipt, deleted = deleted)
        )
    }

    private fun kept(r: Receipt) = receipts.findById(r.id).isPresent && Files.exists(images.resolve(r.imagePath))
    private fun gone(r: Receipt) = receipts.findById(r.id).isEmpty && !Files.exists(images.resolve(r.imagePath))

    @Test
    fun `an unsaved receipt goes after a day, with its photo, and the rest stay`() {
        val p = identity.newMember("cleanup")
        val oldUnlinked = receipt(p, Duration.ofHours(25))
        val oldLinked = receipt(p, Duration.ofHours(25)).also { saveWith(p, it) }
        val oldOnTombstone = receipt(p, Duration.ofHours(25)).also { saveWith(p, it, deleted = true) }
        val fresh = receipt(p, Duration.ofHours(23))

        cleanup.deleteUnlinked()

        assertTrue(gone(oldUnlinked), "an unsaved receipt older than a day is deleted with its photo")
        assertTrue(gone(oldOnTombstone), "a receipt held only by a deleted transaction counts as unsaved")
        assertTrue(kept(oldLinked), "a receipt saved with a transaction stays")
        assertTrue(kept(fresh), "an unsaved receipt younger than a day stays")
        assertFalse(transactions.findAll().any { it.receipt?.id == oldOnTombstone.id })
    }
}
