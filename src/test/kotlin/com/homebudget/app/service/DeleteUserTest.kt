package com.homebudget.app.service

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateTransactionRequest
import com.homebudget.app.dto.SyncAccount
import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.dto.SyncPushRequest
import com.homebudget.app.receipt.Receipt
import com.homebudget.app.receipt.ReceiptImages
import com.homebudget.app.receipt.ReceiptRepository
import com.homebudget.app.receipt.ReceiptStatus
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** What erasing a login leaves behind, with real services and a real schema. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class DeleteUserTest {

    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var transactionService: TransactionService
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var accountRepository: AccountRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var receiptRepository: ReceiptRepository
    @Autowired private lateinit var receiptImages: ReceiptImages
    @Autowired private lateinit var receiptService: com.homebudget.app.receipt.ReceiptService
    @Autowired private lateinit var transactionManager: org.springframework.transaction.PlatformTransactionManager

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun count(sql: String, id: Long): Long = jdbc.queryForObject(sql, Long::class.java, id)!!

    private fun author(transactionId: Long): Long? =
        jdbc.queryForObject("SELECT created_by_user_id FROM transactions WHERE id = ?", Long::class.javaObjectType, transactionId)

    @Test
    fun `deleting one of two members keeps the books, deleting the last one erases them`() {
        val first = identity.newMember("first")
        val second = identity.joinAs("second", first.household)
        val householdId = first.household.id

        identity.signIn(first)
        val accountUuid = UUID.randomUUID().toString()
        val pushed = syncService.push(
            SyncPushRequest(
                SyncPayload(accounts = listOf(SyncAccount(uuid = accountUuid, name = "Shared cash", currencyCode = "USD")))
            )
        )
        assertTrue(pushed.rejected.isEmpty(), "setup push was rejected: ${pushed.rejected}")
        val accountId = accountRepository.findByUuidAndHousehold(accountUuid, first.household)!!.id

        identity.signIn(second)
        val transactionId = transactionService.create(
            CreateTransactionRequest(
                accountId = accountId,
                amount = BigDecimal("7.00"),
                type = TransactionType.EXPENSE,
                date = LocalDate.now()
            )
        ).id
        assertEquals(second.user.id, author(transactionId))

        // One of two members goes: the household, its account and the entry stay.
        userService.deleteUser(second.user)
        assertFalse(users.existsById(second.user.id))
        assertEquals(1L, count("SELECT COUNT(*) FROM households WHERE id = ?", householdId))
        assertEquals(1L, count("SELECT COUNT(*) FROM accounts WHERE household_id = ?", householdId))
        assertEquals(1L, count("SELECT COUNT(*) FROM transactions WHERE id = ?", transactionId))
        assertNull(author(transactionId))
        assertEquals(1L, count("SELECT COUNT(*) FROM household_members WHERE household_id = ?", householdId))

        // The last member goes: nothing of the household is left.
        userService.deleteUser(first.user)
        assertFalse(users.existsById(first.user.id))
        assertEquals(0L, count("SELECT COUNT(*) FROM households WHERE id = ?", householdId))
        assertEquals(0L, count("SELECT COUNT(*) FROM accounts WHERE household_id = ?", householdId))
        assertEquals(0L, count("SELECT COUNT(*) FROM transactions WHERE household_id = ?", householdId))
        assertEquals(0L, count("SELECT COUNT(*) FROM household_members WHERE household_id = ?", householdId))
    }

    @Test
    fun `deleting the last member deletes the household's receipt photos`() {
        val owner = identity.newMember("photos")
        val png = java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val receipts = (1..2).map {
            val uuid = UUID.randomUUID().toString()
            receiptRepository.save(
                Receipt(uuid = uuid, household = owner.household, status = ReceiptStatus.READY,
                    imagePath = receiptImages.store(png, owner.household.uuid, uuid))
            )
        }
        // One of them saved with an entry, the other not.
        identity.signIn(owner)
        val accountUuid = UUID.randomUUID().toString()
        syncService.push(SyncPushRequest(SyncPayload(accounts = listOf(SyncAccount(uuid = accountUuid, name = "Cash", currencyCode = "USD")))))
        transactionService.create(
            CreateTransactionRequest(
                accountId = accountRepository.findByUuidAndHousehold(accountUuid, owner.household)!!.id,
                amount = BigDecimal("2.00"), type = TransactionType.EXPENSE, date = LocalDate.now(),
                receiptUuid = receipts[0].uuid
            )
        )
        receipts.forEach { assertTrue(java.nio.file.Files.exists(receiptImages.resolve(it.imagePath!!))) }

        userService.deleteUser(owner.user)

        assertEquals(0L, count("SELECT COUNT(*) FROM receipts WHERE household_id = ?", owner.household.id))
        receipts.forEach {
            assertFalse(java.nio.file.Files.exists(receiptImages.resolve(it.imagePath!!)), "photo ${it.imagePath} is deleted")
        }
    }

    @Test
    fun `deleting the last member deletes a household whose receipts include one without a photo`() {
        val owner = identity.newMember("textonly")
        val png = java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val uuid = UUID.randomUUID().toString()
        val photo = receiptRepository.save(
            Receipt(uuid = uuid, household = owner.household, status = ReceiptStatus.READY,
                imagePath = receiptImages.store(png, owner.household.uuid, uuid))
        )
        val textOnly = org.springframework.transaction.support.TransactionTemplate(transactionManager).execute {
            receiptService.createFromAssistant(owner.household.id, owner.user.id, "Shop", LocalDate.now(), "USD", null,
                listOf(com.homebudget.app.receipt.AssistantReceiptItem("Milk", null, 150)))
        }!!
        assertNull(textOnly.imagePath)

        userService.deleteUser(owner.user)

        assertFalse(users.existsById(owner.user.id))
        assertEquals(0L, count("SELECT COUNT(*) FROM receipts WHERE household_id = ?", owner.household.id))
        assertFalse(java.nio.file.Files.exists(receiptImages.resolve(photo.imagePath!!)), "the photo is deleted")
    }
}
