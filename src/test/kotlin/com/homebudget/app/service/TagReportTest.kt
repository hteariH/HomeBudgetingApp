package com.homebudget.app.service

import com.homebudget.app.controller.AutoTagRequest
import com.homebudget.app.controller.CreateTagRequest
import com.homebudget.app.controller.TagController
import com.homebudget.app.domain.*
import com.homebudget.app.dto.*
import com.homebudget.app.repository.*
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class TagReportTest {

    @Autowired private lateinit var tagController: TagController
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var tagRepository: TagRepository
    @Autowired private lateinit var transactionTagRepository: TransactionTagRepository
    @Autowired private lateinit var transactionRepository: TransactionRepository
    @Autowired private lateinit var accountRepository: AccountRepository
    @Autowired private lateinit var categoryRepository: CategoryRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    private lateinit var user: User
    private lateinit var account: Account
    private lateinit var category: Category

    @BeforeEach
    fun setUp() {
        val usd = currencyRepository.findByCode("USD")!!
        user = userRepository.save(
            User(
                email = "tag-test-${UUID.randomUUID()}@example.com",
                passwordHash = "x",
                firstName = "Tag",
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
                name = "Main",
                currency = usd,
                initialBalanceMinor = 100000L
            )
        )

        category = categoryRepository.save(
            Category(user = user, name = "Travel", type = CategoryType.EXPENSE)
        )
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `autoTag extracts hashtags from description and binds them to transaction`() {
        val tx = transactionRepository.save(
            Transaction(
                user = user,
                account = account,
                category = category,
                amountMinor = 15000L, // $150.00
                currencyCode = "USD",
                accountAmountMinor = 15000L,
                type = TransactionType.EXPENSE,
                occurredAt = Instant.now(),
                description = "Flight tickets for #vacation_2026 and #europe-trip"
            )
        )

        val autoTagResponse = tagController.autoTag(
            AutoTagRequest(
                description = tx.description!!,
                transactionId = tx.id
            )
        )

        assertEquals(2, autoTagResponse.detectedTags.size)
        assertTrue(autoTagResponse.detectedTags.contains("vacation_2026"))
        assertTrue(autoTagResponse.detectedTags.contains("europe-trip"))

        val bound = transactionTagRepository.findByTransactionId(tx.id)
        assertEquals(2, bound.size)
        assertTrue(bound.any { it.tag.name == "vacation_2026" })
        assertTrue(bound.any { it.tag.name == "europe-trip" })
    }

    @Test
    fun `tag report aggregates expense and income across tagged transactions`() {
        val tagResponse = tagController.create(CreateTagRequest(name = "renovation", color = "#EF4444"))
        val tag = tagRepository.findById(tagResponse.id).get()

        val tx1 = transactionRepository.save(
            Transaction(
                user = user,
                account = account,
                category = category,
                amountMinor = 30000L, // $300.00 expense
                currencyCode = "USD",
                accountAmountMinor = 30000L,
                type = TransactionType.EXPENSE,
                occurredAt = Instant.now(),
                description = "Paint and brushes"
            )
        )
        val tx2 = transactionRepository.save(
            Transaction(
                user = user,
                account = account,
                category = null,
                amountMinor = 5000L, // $50.00 expense
                currencyCode = "USD",
                accountAmountMinor = 5000L,
                type = TransactionType.EXPENSE,
                occurredAt = Instant.now(),
                description = "Hardware supplies"
            )
        )

        transactionTagRepository.save(TransactionTag(user = user, transaction = tx1, tag = tag))
        transactionTagRepository.save(TransactionTag(user = user, transaction = tx2, tag = tag))

        val report = tagController.getReport(tag.id)
        assertEquals(tag.id, report.tagId)
        assertEquals("renovation", report.tagName)
        assertEquals(2, report.totalTransactions)
        assertEquals(BigDecimal("350.00"), report.totalExpense)
        assertEquals(BigDecimal.ZERO, report.totalIncome)
        assertEquals(2, report.transactions.size)
    }

    @Test
    fun `sync push and pull round-trip for tags and transaction tags`() {
        val tagUuid = UUID.randomUUID().toString()
        val tx = transactionRepository.save(
            Transaction(
                user = user,
                account = account,
                amountMinor = 2000L,
                currencyCode = "USD",
                accountAmountMinor = 2000L,
                type = TransactionType.EXPENSE,
                occurredAt = Instant.now()
            )
        )
        val txTagUuid = UUID.randomUUID().toString()

        val pushRequest = SyncPushRequest(
            SyncPayload(
                tags = listOf(
                    SyncTag(
                        uuid = tagUuid,
                        name = "conference",
                        color = "#10B981"
                    )
                ),
                transactionTags = listOf(
                    SyncTransactionTag(
                        uuid = txTagUuid,
                        transactionUuid = tx.uuid,
                        tagUuid = tagUuid
                    )
                )
            )
        )

        val pushResponse = syncService.push(pushRequest)
        assertEquals(2, pushResponse.applied)
        assertEquals(0, pushResponse.rejected.size)

        val pullResponse = syncService.pull(java.time.Instant.EPOCH, SyncService.MAX_PAGE)
        val pulledTag = pullResponse.changes.tags.firstOrNull { it.uuid == tagUuid }
        val pulledTxTag = pullResponse.changes.transactionTags.firstOrNull { it.uuid == txTagUuid }

        assertNotNull(pulledTag)
        assertEquals("conference", pulledTag!!.name)
        assertNotNull(pulledTxTag)
        assertEquals(tx.uuid, pulledTxTag!!.transactionUuid)
        assertEquals(tagUuid, pulledTxTag.tagUuid)
    }
}
