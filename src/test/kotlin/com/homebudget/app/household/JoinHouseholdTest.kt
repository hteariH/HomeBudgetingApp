package com.homebudget.app.household

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.ExchangeRate
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.RateSource
import com.homebudget.app.domain.SubscriptionFrequency
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.*
import com.homebudget.app.repository.*
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.SyncService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Accepting an invitation: the joiner and every row they kept move into the family. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class JoinHouseholdTest {

    @Autowired lateinit var identity: TestIdentity
    @Autowired lateinit var syncService: SyncService
    @Autowired lateinit var inviteService: InviteService
    @Autowired lateinit var mergeService: HouseholdMergeService
    @Autowired lateinit var inviteRepository: HouseholdInviteRepository
    @Autowired lateinit var householdRepository: HouseholdRepository
    @Autowired lateinit var householdMemberRepository: HouseholdMemberRepository
    @Autowired lateinit var accountRepository: AccountRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var transactionRepository: TransactionRepository
    @Autowired lateinit var exchangeRateRepository: ExchangeRateRepository
    @Autowired lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun uuid() = UUID.randomUUID().toString()

    private fun push(who: UserPrincipal, payload: SyncPayload): Instant {
        identity.signIn(who)
        try {
            val pushed = syncService.push(SyncPushRequest(payload))
            assertTrue(pushed.rejected.isEmpty(), "push was rejected: ${pushed.rejected}")
            return pushed.serverTime
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun inviteFrom(owner: UserPrincipal): String {
        identity.signIn(owner)
        try {
            return inviteService.create(null).path.substringAfter("token=")
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    private fun join(who: UserPrincipal, token: String) =
        mergeService.join(who.user, inviteService.requireLive(token))

    private fun expense(uuid: String, account: String, category: String?) = SyncTransaction(
        uuid = uuid, accountUuid = account, categoryUuid = category,
        amount = BigDecimal("12.50"), currencyCode = "USD", accountAmount = BigDecimal("12.50"),
        type = TransactionType.EXPENSE, occurredAt = Instant.parse("2026-10-01T10:00:00Z")
    )

    private fun liveExpenseCategories(owner: UserPrincipal, name: String) =
        categoryRepository.findByHouseholdAndType(owner.household, CategoryType.EXPENSE).filter { it.name == name }

    @Test
    fun `joining moves the joiner's books into the family`() {
        val anna = identity.newMember("anna")
        val annaFood = uuid()
        push(anna, SyncPayload(
            accounts = listOf(SyncAccount(uuid = uuid(), name = "Family cash", currencyCode = "USD")),
            categories = listOf(SyncCategory(uuid = annaFood, name = "Food", type = CategoryType.EXPENSE))
        ))

        val boris = identity.newMember("boris")
        val card = uuid()
        val borisFood = uuid()
        val tx = uuid()
        val sub = uuid()
        push(boris, SyncPayload(
            currencySettings = listOf(SyncCurrencySetting(uuid = uuid(), currencyCode = "EUR", active = true)),
            accounts = listOf(SyncAccount(uuid = card, name = "Boris card", currencyCode = "USD")),
            categories = listOf(
                SyncCategory(uuid = borisFood, name = "Food", type = CategoryType.EXPENSE),
                SyncCategory(uuid = uuid(), name = "Hobby", type = CategoryType.EXPENSE)
            ),
            subscriptions = listOf(
                SyncSubscription(
                    uuid = sub, name = "Boris gym", accountUuid = card, categoryUuid = borisFood,
                    amount = BigDecimal("30.00"), currencyCode = "USD", frequency = SubscriptionFrequency.MONTHLY,
                    nextChargeDate = LocalDate.of(2026, 11, 1), startDate = LocalDate.of(2026, 1, 1)
                )
            ),
            transactions = listOf(expense(tx, card, borisFood))
        ))

        val token = inviteFrom(anna)
        identity.signIn(boris)
        val joined = join(boris, token)
        assertEquals(anna.household.id, joined.id)

        assertEquals(
            setOf("Family cash", "Boris card"),
            accountRepository.findAllByHousehold(anna.household).map { it.name }.toSet()
        )
        val food = liveExpenseCategories(anna, "Food")
        assertEquals(1, food.size)
        assertEquals(annaFood, food.single().uuid)
        assertEquals(1, transactionRepository.countByCategoryId(food.single().id), "the transaction follows the merge")
        assertEquals(1, subscriptionRepository.countByCategoryId(food.single().id), "the subscription follows the merge")
        assertNotNull(transactionRepository.findByUuidAndHousehold(tx, anna.household))
        assertEquals(1, liveExpenseCategories(anna, "Hobby").size)
        assertNotNull(subscriptionRepository.findByUuidAndHousehold(sub, anna.household))
        assertEquals(
            listOf("EUR"),
            jdbc.queryForList(
                "SELECT c.code FROM household_currency_settings s JOIN currencies c ON c.id = s.currency_id WHERE s.household_id = ?",
                String::class.java, anna.household.id
            )
        )

        assertFalse(householdRepository.existsById(boris.household.id))
        val membership = householdMemberRepository.findFirstByUserId(boris.user.id)!!
        assertEquals(anna.household.id, membership.household.id)
        assertEquals(HouseholdRole.MEMBER, membership.role)
        assertNotNull(inviteRepository.findByTokenHash(InviteService.hash(token))!!.usedAt)
    }

    @Test
    fun `on a clash of currency settings or manual rates the family's own win`() {
        val anna = identity.newMember("anna")
        push(anna, SyncPayload(currencySettings = listOf(
            SyncCurrencySetting(uuid = uuid(), currencyCode = "USD", active = true, sortOrder = 1)
        )))
        val boris = identity.newMember("boris")
        push(boris, SyncPayload(currencySettings = listOf(
            SyncCurrencySetting(uuid = uuid(), currencyCode = "USD", active = false, sortOrder = 7),
            SyncCurrencySetting(uuid = uuid(), currencyCode = "EUR", active = true)
        )))
        fun rate(owner: UserPrincipal, quote: String, value: String) = exchangeRateRepository.save(ExchangeRate(
            baseCode = "USD", quoteCode = quote, rate = BigDecimal(value), rateDate = LocalDate.of(2026, 10, 1),
            source = RateSource.MANUAL, household = owner.household
        ))
        rate(anna, "UAH", "41")
        rate(boris, "UAH", "40")
        rate(boris, "PLN", "4")

        join(boris, inviteFrom(anna))

        val settings = jdbc.queryForList(
            "SELECT c.code, s.active, s.sort_order FROM household_currency_settings s JOIN currencies c ON c.id = s.currency_id WHERE s.household_id = ? ORDER BY c.code",
            anna.household.id
        ).map { "${it["code"]}:${it["active"]}:${it["sort_order"]}" }
        assertEquals(listOf("EUR:true:0", "USD:true:1"), settings)

        val rates = jdbc.queryForList(
            "SELECT quote_code, rate FROM exchange_rates WHERE household_id = ? ORDER BY quote_code",
            anna.household.id
        ).map { "${it["quote_code"]}=${(it["rate"] as BigDecimal).stripTrailingZeros().toPlainString()}" }
        assertEquals(listOf("PLN=4", "UAH=41"), rates)
    }

    @Test
    fun `a sub-category under a merged parent merges into the family's one`() {
        val anna = identity.newMember("anna")
        val annaFood = uuid()
        val annaGroceries = uuid()
        push(anna, SyncPayload(categories = listOf(
            SyncCategory(uuid = annaFood, name = "Food", type = CategoryType.EXPENSE),
            SyncCategory(uuid = annaGroceries, name = "Groceries", type = CategoryType.EXPENSE, parentUuid = annaFood)
        )))
        val boris = identity.newMember("boris")
        val card = uuid()
        val borisFood = uuid()
        val borisGroceries = uuid()
        val borisCafe = uuid()
        push(boris, SyncPayload(
            accounts = listOf(SyncAccount(uuid = card, name = "Boris card", currencyCode = "USD")),
            categories = listOf(
                SyncCategory(uuid = borisFood, name = "food", type = CategoryType.EXPENSE),
                SyncCategory(uuid = borisGroceries, name = "Groceries", type = CategoryType.EXPENSE, parentUuid = borisFood),
                SyncCategory(uuid = borisCafe, name = "Cafe", type = CategoryType.EXPENSE, parentUuid = borisFood)
            ),
            transactions = listOf(expense(uuid(), card, borisGroceries))
        ))

        join(boris, inviteFrom(anna))

        val groceries = liveExpenseCategories(anna, "Groceries")
        assertEquals(listOf(annaGroceries), groceries.map { it.uuid })
        assertEquals(1, transactionRepository.countByCategoryId(groceries.single().id))
        val cafeParent = jdbc.queryForObject(
            "SELECT p.uuid FROM categories c JOIN categories p ON p.id = c.parent_id WHERE c.uuid = ?",
            String::class.java, borisCafe
        )
        assertEquals(annaFood, cafeParent, "an unmatched child hangs under the family's parent")
        assertTrue(liveExpenseCategories(anna, "food").isEmpty(), "matching ignores case")
    }

    @Test
    fun `another member's next pull sees the moved rows`() {
        val anna = identity.newMember("anna")
        push(anna, SyncPayload(accounts = listOf(SyncAccount(uuid = uuid(), name = "Family cash", currencyCode = "USD"))))
        val boris = identity.newMember("boris")
        val card = uuid()
        val tx = uuid()
        push(boris, SyncPayload(
            accounts = listOf(SyncAccount(uuid = card, name = "Boris card", currencyCode = "USD")),
            transactions = listOf(expense(tx, card, null))
        ))
        val token = inviteFrom(anna)

        identity.signIn(anna)
        val cursor = syncService.pull(Instant.EPOCH, 500).serverTime
        join(boris, token)

        identity.signIn(anna)
        val changes = syncService.pull(cursor, 500).changes
        assertTrue(changes.accounts.any { it.name == "Boris card" }, "moved account missing from the pull")
        assertTrue(changes.transactions.any { it.uuid == tx }, "moved transaction missing from the pull")
    }

    @Test
    fun `an owner whose household has other members cannot join elsewhere`() {
        val anna = identity.newMember("anna")
        val boris = identity.newMember("boris")
        identity.joinAs("clara", boris.household)
        val token = inviteFrom(anna)

        assertThrows<IllegalArgumentException> { join(boris, token) }
        assertEquals(boris.household.id, householdMemberRepository.findFirstByUserId(boris.user.id)!!.household.id)
        assertNotNull(inviteService.requireLive(token), "a refused join leaves the invite usable")
    }

    @Test
    fun `a member of a shared household joins elsewhere without taking the family's books`() {
        val anna = identity.newMember("anna")
        val boris = identity.newMember("boris")
        push(boris, SyncPayload(accounts = listOf(SyncAccount(uuid = uuid(), name = "Boris card", currencyCode = "USD"))))
        val clara = identity.joinAs("clara", boris.household)

        join(clara, inviteFrom(anna))

        assertEquals(anna.household.id, householdMemberRepository.findFirstByUserId(clara.user.id)!!.household.id)
        assertTrue(householdRepository.existsById(boris.household.id))
        assertEquals(boris.household.id, householdMemberRepository.findFirstByUserId(boris.user.id)!!.household.id)
        assertEquals(listOf("Boris card"), accountRepository.findAllByHousehold(boris.household).map { it.name })
        assertTrue(accountRepository.findAllByHousehold(anna.household).isEmpty())
    }

    @Test
    fun `an invite cannot be used into one's own household`() {
        val anna = identity.newMember("anna")
        val token = inviteFrom(anna)
        assertThrows<IllegalArgumentException> { join(anna, token) }
    }

    @Test
    fun `an invite works once`() {
        val anna = identity.newMember("anna")
        val boris = identity.newMember("boris")
        val token = inviteFrom(anna)
        join(boris, token)
        assertThrows<InviteGoneException> { inviteService.requireLive(token) }
    }

    @Test
    fun `a join and a sync push into the same household finish together`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(10) { round ->
                // Alternate who has the lower id, so both lock orders are exercised.
                val (anna, boris) = if (round % 2 == 0) {
                    identity.newMember("anna").let { it to identity.newMember("boris") }
                } else {
                    identity.newMember("boris").let { identity.newMember("anna") to it }
                }
                push(boris, SyncPayload(accounts = listOf(SyncAccount(uuid = uuid(), name = "moved", currencyCode = "USD"))))
                val invite = inviteService.requireLive(inviteFrom(anna))

                val errors = Collections.synchronizedList(mutableListOf<Throwable>())
                val start = CountDownLatch(1)
                val done = CountDownLatch(2)
                val tasks = listOf(
                    Runnable { mergeService.join(boris.user, invite) },
                    Runnable {
                        push(anna, SyncPayload(accounts = listOf(SyncAccount(uuid = uuid(), name = "pushed", currencyCode = "USD"))))
                    }
                )
                for (task in tasks) {
                    pool.execute {
                        try {
                            start.await()
                            task.run()
                        } catch (e: Throwable) {
                            errors += e
                        } finally {
                            done.countDown()
                        }
                    }
                }
                start.countDown()
                assertTrue(done.await(10, TimeUnit.SECONDS), "round $round did not finish")
                assertTrue(errors.isEmpty(), "round $round failed: $errors")

                identity.signIn(anna)
                val names = syncService.pull(Instant.EPOCH, 500).changes.accounts.map { it.name }.toSet()
                assertEquals(setOf("moved", "pushed"), names, "round $round")
                SecurityContextHolder.clearContext()
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
