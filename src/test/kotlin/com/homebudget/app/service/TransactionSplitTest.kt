package com.homebudget.app.service

import com.homebudget.app.controller.SaveSplitsRequest
import com.homebudget.app.controller.SplitItemRequest
import com.homebudget.app.controller.TransactionSplitController
import com.homebudget.app.domain.*
import com.homebudget.app.dto.*
import com.homebudget.app.repository.*
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class TransactionSplitTest {

    @Autowired private lateinit var splitController: TransactionSplitController
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository
    @Autowired private lateinit var accountRepository: AccountRepository
    @Autowired private lateinit var categoryRepository: CategoryRepository
    @Autowired private lateinit var transactionRepository: TransactionRepository
    @Autowired private lateinit var splitRepository: TransactionSplitRepository

    private lateinit var user: User
    private lateinit var account: Account
    private lateinit var catFood: Category
    private lateinit var catTransport: Category
    private lateinit var tx: Transaction

    @BeforeEach
    fun setUp() {
        val usd = currencyRepository.findByCode("USD")!!
        user = userRepository.save(
            User(
                email = "split-test-${UUID.randomUUID()}@example.com",
                passwordHash = "x",
                firstName = "Split",
                lastName = "Tester",
                role = Role.USER,
                baseCurrency = usd
            )
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())

        account = accountRepository.save(
            Account(
                user = user,
                name = "Checking",
                currency = usd,
                initialBalanceMinor = 100000L
            )
        )

        catFood = categoryRepository.save(
            Category(user = user, name = "Food", type = CategoryType.EXPENSE)
        )
        catTransport = categoryRepository.save(
            Category(user = user, name = "Transport", type = CategoryType.EXPENSE)
        )

        tx = transactionRepository.save(
            Transaction(
                user = user,
                account = account,
                category = catFood,
                amountMinor = 10000L, // $100.00
                currencyCode = "USD",
                accountAmountMinor = 10000L,
                type = TransactionType.EXPENSE,
                occurredAt = Instant.now()
            )
        )
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `controller rejects splits when sum does not equal transaction amount`() {
        // tx is $100.00 (10000 minor). splits sum to $60.00 + $30.00 = $90.00
        val request = SaveSplitsRequest(
            splits = listOf(
                SplitItemRequest(categoryId = catFood.id, amount = BigDecimal("60.00"), note = "Groceries"),
                SplitItemRequest(categoryId = catTransport.id, amount = BigDecimal("30.00"), note = "Bus")
            )
        )

        val ex = assertThrows<ResponseStatusException> {
            splitController.saveSplits(tx.id, request)
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.statusCode)
    }

    @Test
    fun `controller accepts valid splits summing up to transaction amount`() {
        val request = SaveSplitsRequest(
            splits = listOf(
                SplitItemRequest(categoryId = catFood.id, amount = BigDecimal("70.00"), note = "Dinner"),
                SplitItemRequest(categoryId = catTransport.id, amount = BigDecimal("30.00"), note = "Taxi")
            )
        )

        val response = splitController.saveSplits(tx.id, request)
        assertEquals(2, response.size)
        assertEquals(BigDecimal("70.00"), response[0].amount)
        assertEquals(BigDecimal("30.00"), response[1].amount)

        val retrieved = splitController.getSplits(tx.id)
        assertEquals(2, retrieved.size)
        assertEquals("Food", retrieved[0].categoryName)
        assertEquals("Transport", retrieved[1].categoryName)
    }

    @Test
    fun `sync push rejects splits when sum does not equal transaction amount`() {
        val txUuid = tx.uuid
        val split1Uuid = UUID.randomUUID().toString()
        val split2Uuid = UUID.randomUUID().toString()

        val pushRequest = SyncPushRequest(
            SyncPayload(
                transactionSplits = listOf(
                    SyncTransactionSplit(
                        uuid = split1Uuid,
                        transactionUuid = txUuid,
                        categoryUuid = catFood.uuid,
                        amount = BigDecimal("40.00"),
                        note = "Part 1"
                    ),
                    SyncTransactionSplit(
                        uuid = split2Uuid,
                        transactionUuid = txUuid,
                        categoryUuid = catTransport.uuid,
                        amount = BigDecimal("40.00"), // 40 + 40 = 80 != 100
                        note = "Part 2"
                    )
                )
            )
        )

        val response = syncService.push(pushRequest)
        assertEquals(0, response.applied)
        assertEquals(2, response.rejected.size)
        assertEquals(SyncRejectionReason.INVALID, response.rejected[0].reason)
    }

    @Test
    fun `sync push accepts valid splits and pull retrieves them`() {
        val txUuid = tx.uuid
        val split1Uuid = UUID.randomUUID().toString()
        val split2Uuid = UUID.randomUUID().toString()

        val pushRequest = SyncPushRequest(
            SyncPayload(
                transactionSplits = listOf(
                    SyncTransactionSplit(
                        uuid = split1Uuid,
                        transactionUuid = txUuid,
                        categoryUuid = catFood.uuid,
                        amount = BigDecimal("65.00"),
                        note = "Lunch"
                    ),
                    SyncTransactionSplit(
                        uuid = split2Uuid,
                        transactionUuid = txUuid,
                        categoryUuid = catTransport.uuid,
                        amount = BigDecimal("35.00"),
                        note = "Metro"
                    )
                )
            )
        )

        val pushResponse = syncService.push(pushRequest)
        assertEquals(2, pushResponse.applied)
        assertEquals(0, pushResponse.rejected.size)

        val pullResponse = syncService.pull(Instant.EPOCH, SyncService.MAX_PAGE)
        val pulledSplits = pullResponse.changes.transactionSplits
        assertEquals(2, pulledSplits.size)
        assertTrue(pulledSplits.any { it.uuid == split1Uuid && it.amount.compareTo(BigDecimal("65.00")) == 0 })
        assertTrue(pulledSplits.any { it.uuid == split2Uuid && it.amount.compareTo(BigDecimal("35.00")) == 0 })
    }
}
