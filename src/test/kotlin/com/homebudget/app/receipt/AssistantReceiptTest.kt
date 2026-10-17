package com.homebudget.app.receipt

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.Account
import com.homebudget.app.domain.AccountType
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateTransactionRequest
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.TransactionService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate

/** A receipt an assistant read itself: no photo, saved only together with its transaction. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class AssistantReceiptTest {

    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var service: ReceiptService
    @Autowired private lateinit var receipts: ReceiptRepository
    @Autowired private lateinit var transactions: TransactionService
    @Autowired private lateinit var accounts: AccountRepository
    @Autowired private lateinit var currencies: CurrencyRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var me: UserPrincipal
    private lateinit var card: Account

    @BeforeEach
    fun setUp() {
        me = identity.newMember("assistant-receipt")
        identity.signIn(me)
        card = accounts.save(Account(name = "Card", type = AccountType.CHECKING, household = me.household,
            currency = currencies.findByCode("UAH")!!, initialBalanceMinor = 0))
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun create(total: Long?, vararg prices: Long): Receipt = TransactionTemplate(transactionManager).execute {
        service.createFromAssistant(
            me.household.id, me.user.id, "Shop", LocalDate.parse("2026-10-05"), "UAH", total,
            prices.mapIndexed { i, p -> AssistantReceiptItem("Item $i", null, p) }
        )
    }!!

    @Test
    fun `it is READY without a photo and flags a total its items miss by more than a minor unit each`() {
        val exact = create(null, 1000, 250)
        assertEquals(ReceiptStatus.READY, exact.status)
        assertNull(exact.imagePath)
        assertEquals(1250L, exact.totalMinor)
        assertFalse(exact.totalMismatch)

        assertFalse(create(1252, 1000, 250).totalMismatch) // within one minor unit per item
        assertTrue(create(1253, 1000, 250).totalMismatch)

        val response = service.get(exact.uuid)
        assertFalse(response.hasImage)
        assertEquals(listOf("10.00", "2.50"), response.items.map { it.price })
        assertThrows<NoSuchElementException> { service.image(exact.uuid) }
    }

    @Test
    fun `it is only created inside the caller's transaction`() {
        assertThrows<IllegalTransactionStateException> {
            service.createFromAssistant(me.household.id, me.user.id, null, LocalDate.parse("2026-10-05"), "UAH", null, emptyList())
        }
    }

    @Test
    fun `deleting its transaction deletes it, with no photo to remove`() {
        val receipt = create(null, 500)
        val t = transactions.create(
            CreateTransactionRequest(accountId = card.id, amount = BigDecimal("5.00"), type = TransactionType.EXPENSE,
                date = LocalDate.parse("2026-10-05"), receiptUuid = receipt.uuid)
        )
        transactions.delete(t.id)
        assertTrue(receipts.findById(receipt.id).isEmpty)
    }
}
