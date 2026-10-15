package com.homebudget.app

import com.homebudget.app.bank.BankConnectionService
import com.homebudget.app.domain.BankConnection
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.ExchangeRate
import com.homebudget.app.domain.RateSource
import com.homebudget.app.domain.SubscriptionFrequency
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.*
import com.homebudget.app.repository.*
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The regression net for scoping the ledger by household: nothing one household owns is
 * readable or writable from another, by REST services or by sync - and everything is
 * shared between two logins of the same household.
 *
 * Open so [PostgresHouseholdIsolationTest] can run the same cases on Postgres.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
open class HouseholdIsolationTest {

    @Autowired lateinit var identity: TestIdentity
    @Autowired lateinit var syncService: SyncService
    @Autowired lateinit var accountService: AccountService
    @Autowired lateinit var categoryService: CategoryService
    @Autowired lateinit var subscriptionService: SubscriptionService
    @Autowired lateinit var transactionService: TransactionService
    @Autowired lateinit var transferService: TransferService
    @Autowired lateinit var bankConnectionService: BankConnectionService
    @Autowired lateinit var accountRepository: AccountRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var transactionRepository: TransactionRepository
    @Autowired lateinit var bankConnectionRepository: BankConnectionRepository
    @Autowired lateinit var exchangeRateRepository: ExchangeRateRepository

    private lateinit var alice: UserPrincipal
    private lateinit var bob: UserPrincipal

    private val accountUuid = uuid()
    private val categoryUuid = uuid()
    private val subscriptionUuid = uuid()
    private val transactionUuid = uuid()

    @BeforeEach
    fun setUp() {
        alice = identity.newMember("alice")
        bob = identity.newMember("bob")
        identity.signIn(alice)
        val pushed = syncService.push(
            SyncPushRequest(
                SyncPayload(
                    currencySettings = listOf(SyncCurrencySetting(uuid = uuid(), currencyCode = "EUR", active = true)),
                    accounts = listOf(SyncAccount(uuid = accountUuid, name = "Alice cash", currencyCode = "USD")),
                    categories = listOf(SyncCategory(uuid = categoryUuid, name = "Alice food", type = CategoryType.EXPENSE)),
                    subscriptions = listOf(
                        SyncSubscription(
                            uuid = subscriptionUuid, name = "Alice gym", accountUuid = accountUuid,
                            amount = BigDecimal("30.00"), currencyCode = "USD",
                            frequency = SubscriptionFrequency.MONTHLY,
                            nextChargeDate = LocalDate.of(2026, 11, 1), startDate = LocalDate.of(2026, 1, 1)
                        )
                    ),
                    transactions = listOf(
                        SyncTransaction(
                            uuid = transactionUuid, accountUuid = accountUuid, categoryUuid = categoryUuid,
                            amount = BigDecimal("12.50"), currencyCode = "USD", accountAmount = BigDecimal("12.50"),
                            type = TransactionType.EXPENSE, occurredAt = Instant.parse("2026-10-01T10:00:00Z")
                        )
                    )
                )
            )
        )
        assertTrue(pushed.rejected.isEmpty(), "setup push was rejected: ${pushed.rejected}")
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun uuid() = UUID.randomUUID().toString()
    private fun aliceAccountId() = accountRepository.findByUuidAndHousehold(accountUuid, alice.household)!!.id
    private fun aliceCategoryId() = categoryRepository.findByUuidAndHousehold(categoryUuid, alice.household)!!.id
    private fun aliceSubscriptionId() = subscriptionRepository.findByUuidAndHousehold(subscriptionUuid, alice.household)!!.id
    private fun aliceTransactionId() = transactionRepository.findByUuidAndHousehold(transactionUuid, alice.household)!!.id

    private fun restExpense(accountId: Long, categoryId: Long) = CreateTransactionRequest(
        accountId = accountId,
        categoryId = categoryId,
        amount = BigDecimal("5.00"),
        type = TransactionType.EXPENSE,
        date = LocalDate.now()
    )

    @Test
    fun `another household cannot read any of the ledger through the services`() {
        identity.signIn(bob)
        assertTrue(accountService.findAll().isEmpty())
        assertThrows<NoSuchElementException> { accountService.findById(aliceAccountId()) }
        assertThrows<NoSuchElementException> { categoryService.findById(aliceCategoryId()) }
        assertThrows<NoSuchElementException> { subscriptionService.findById(aliceSubscriptionId()) }
        assertThrows<NoSuchElementException> { transactionService.findById(aliceTransactionId()) }
    }

    @Test
    fun `another household cannot change or delete the ledger through the services`() {
        identity.signIn(bob)
        assertThrows<NoSuchElementException> { accountService.update(aliceAccountId(), UpdateAccountRequest(name = "mine now")) }
        assertThrows<NoSuchElementException> { accountService.delete(aliceAccountId()) }
        assertThrows<NoSuchElementException> { transactionService.delete(aliceTransactionId()) }
        assertThrows<NoSuchElementException> { categoryService.delete(aliceCategoryId()) }
        assertThrows<NoSuchElementException> { subscriptionService.delete(aliceSubscriptionId()) }

        identity.signIn(alice)
        assertEquals("Alice cash", accountService.findById(aliceAccountId()).name)
    }

    @Test
    fun `a pull from another household sees nothing`() {
        identity.signIn(bob)
        val changes = syncService.pull(Instant.EPOCH, 500).changes
        assertTrue(changes.accounts.isEmpty())
        assertTrue(changes.categories.isEmpty())
        assertTrue(changes.subscriptions.isEmpty())
        assertTrue(changes.transactions.isEmpty())
        assertTrue(changes.currencySettings.isEmpty())
    }

    @Test
    fun `a push from another household cannot point at this household's rows`() {
        identity.signIn(bob)
        val response = syncService.push(
            SyncPushRequest(
                SyncPayload(
                    transactions = listOf(
                        SyncTransaction(
                            uuid = uuid(), accountUuid = accountUuid,
                            amount = BigDecimal("999.00"), currencyCode = "USD", accountAmount = BigDecimal("999.00"),
                            type = TransactionType.EXPENSE, occurredAt = Instant.parse("2026-10-02T10:00:00Z")
                        )
                    )
                )
            )
        )
        assertEquals(0, response.applied)
        assertEquals(SyncRejectionReason.UNKNOWN_REFERENCE, response.rejected.single().reason)
    }

    @Test
    fun `bank connections and manual rates stay in their household`() {
        val connection = bankConnectionRepository.save(
            BankConnection(household = alice.household, connectedBy = alice.user, provider = "MONOBANK", credentials = "x", label = "Alice bank")
        )
        exchangeRateRepository.save(
            ExchangeRate(
                baseCode = "XAU", quoteCode = "USD", rate = BigDecimal("2400"),
                rateDate = LocalDate.of(2026, 10, 1), source = RateSource.MANUAL, household = alice.household
            )
        )

        identity.signIn(bob)
        assertTrue(bankConnectionService.findAll().none { it.id == connection.id })
        assertThrows<NoSuchElementException> { bankConnectionService.findById(connection.id) }
        assertTrue(exchangeRateRepository.latestFor(bob.household).none { it.household != null })
    }

    @Test
    fun `a second login in the same household sees and edits the same ledger`() {
        val partner = identity.joinAs("partner", alice.household)
        identity.signIn(partner)

        assertEquals(listOf("Alice cash"), accountService.findAll().map { it.name })
        assertEquals(1, syncService.pull(Instant.EPOCH, 500).changes.transactions.size)
        accountService.update(aliceAccountId(), UpdateAccountRequest(name = "Shared cash"))

        identity.signIn(alice)
        assertEquals("Shared cash", accountService.findById(aliceAccountId()).name)
    }

    @Test
    fun `a transaction records who wrote it, by sync and over REST`() {
        val synced = transactionRepository.findByUuidAndHousehold(transactionUuid, alice.household)!!
        assertEquals(alice.user.id, synced.createdBy?.id)

        val partner = identity.joinAs("partner", alice.household)
        identity.signIn(partner)
        val created = transactionService.create(restExpense(aliceAccountId(), aliceCategoryId()))
        assertEquals(partner.user.id, transactionRepository.findById(created.id).orElseThrow().createdBy?.id)
    }

    @Test
    fun `both legs of a transfer record who wrote it`() {
        val partner = identity.joinAs("partner", alice.household)
        identity.signIn(partner)
        val from = accountService.findById(aliceAccountId())
        val to = accountService.create(CreateAccountRequest(name = "Partner savings", currencyId = from.currency.id))

        val debit = transferService.create(
            CreateTransferRequest(
                fromAccountId = from.id, toAccountId = to.id,
                fromAmount = BigDecimal("20.00"), toAmount = BigDecimal("20.00"),
                date = LocalDate.now()
            )
        )

        val legs = transactionRepository.findAllByTransferGroupIdAndHousehold(debit.transferGroupId!!, alice.household)
        assertEquals(2, legs.size)
        assertTrue(legs.all { it.createdBy?.id == partner.user.id }, "every leg carries its author")
    }
}
