package com.homebudget.app.billing

import com.homebudget.app.domain.EntitlementSource
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.domain.isPremium
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.PlayPurchaseRepository
import com.homebudget.app.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Verifying a Play purchase, with Google answering from a script.
 *
 * What is worth testing here is not the HTTP - that is [GooglePlayDeveloperApi]'s, and it
 * cannot be exercised without a real service account - but what this server does with the
 * answer: which subscription states are actually paid for, that one receipt cannot buy two
 * accounts, and that a lapse takes the entitlement away again without disturbing one
 * granted by hand.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlayBillingServiceTest {

    /**
     * Google, as a script. A recorded implementation rather than a mock, for the reason
     * `AuthFlowServiceTest` gives: Mockito cannot instrument classes on this JDK.
     */
    class ScriptedPlayApi : PlayDeveloperApi {
        var reachable = true
        var answer: PlaySubscription? = null
        var failWith: RuntimeException? = null
        val acknowledged = mutableListOf<Pair<String, String>>()

        override val available: Boolean get() = reachable

        override fun subscription(purchaseToken: String): PlaySubscription {
            failWith?.let { throw it }
            return answer ?: throw InvalidPurchaseException("nothing scripted")
        }

        override fun acknowledge(productId: String, purchaseToken: String) {
            acknowledged += productId to purchaseToken
        }
    }

    @TestConfiguration
    class Play {
        @Bean @Primary fun scriptedPlayApi() = ScriptedPlayApi()
    }

    @Autowired private lateinit var google: ScriptedPlayApi
    @Autowired private lateinit var billing: PlayBillingService
    @Autowired private lateinit var entitlements: EntitlementService
    @Autowired private lateinit var purchases: PlayPurchaseRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var currencies: CurrencyRepository

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        user = newUser()
        google.reachable = true
        google.failWith = null
        google.acknowledged.clear()
        google.answer = null
    }

    private fun newUser() = users.save(
        User(
            email = "billing-${System.nanoTime()}@example.com",
            passwordHash = "x",
            firstName = "Test",
            lastName = "User",
            role = Role.USER,
            baseCurrency = currencies.findByCode("USD")
        )
    )

    private fun googleSays(
        state: String = "SUBSCRIPTION_STATE_ACTIVE",
        expiresInDays: Long? = 30,
        productId: String? = "premium_monthly",
        acknowledged: Boolean = false,
        autoRenewing: Boolean = true
    ) {
        google.failWith = null
        google.answer = PlaySubscription(
            state = state,
            productId = productId,
            expiresAt = expiresInDays?.let { entitlements.now().plus(it, ChronoUnit.DAYS) },
            orderId = "GPA.0000-0000-0000-00000",
            autoRenewing = autoRenewing,
            acknowledged = acknowledged,
            test = true
        )
    }

    // -- a purchase that is good ---------------------------------------------

    @Test
    fun `an active subscription makes the account premium until its expiry`() {
        googleSays()

        val purchase = billing.register(user, "token-active")

        assertTrue(user.isPremium())
        assertEquals(EntitlementSource.PLAY, user.entitlementSource)
        assertEquals(purchase.expiresAt, user.entitlementExpiresAt)
        assertEquals("premium_monthly", purchase.productId)
    }

    @Test
    fun `a purchase Play has not been told about yet is acknowledged`() {
        googleSays(acknowledged = false)

        billing.register(user, "token-unacknowledged")

        assertEquals(listOf("premium_monthly" to "token-unacknowledged"), google.acknowledged)
    }

    @Test
    fun `a purchase Play already knows about is not acknowledged twice`() {
        googleSays(acknowledged = true)

        billing.register(user, "token-acknowledged")

        assertTrue(google.acknowledged.isEmpty(), "it acknowledged: ${google.acknowledged}")
    }

    @Test
    fun `presenting the same token again refreshes rather than duplicates`() {
        googleSays(expiresInDays = 30)
        billing.register(user, "token-restore")

        googleSays(expiresInDays = 60, acknowledged = true)
        billing.register(user, "token-restore")

        assertEquals(1, purchases.findAllByUser(user).size, "one token is one row")
        assertTrue(user.entitlementExpiresAt!!.isAfter(entitlements.now().plus(59, ChronoUnit.DAYS)))
    }

    // -- a purchase that is not ----------------------------------------------

    @Test
    fun `a token already held by another account is refused`() {
        googleSays()
        billing.register(user, "token-shared")
        val stranger = newUser()

        val refused = assertThrows<InvalidPurchaseException> { billing.register(stranger, "token-shared") }

        assertTrue(refused.message!!.contains("another account"), "it read: ${refused.message}")
        assertFalse(stranger.isPremium())
    }

    @Test
    fun `a purchase of something that is not premium is refused`() {
        googleSays(productId = "some_other_product")

        assertThrows<InvalidPurchaseException> { billing.register(user, "token-wrong-product") }

        assertFalse(user.isPremium())
    }

    @Test
    fun `a server with no service account cannot take a purchase at all`() {
        googleSays()
        google.reachable = false

        assertThrows<BillingUnavailableException> { billing.register(user, "token-anything") }
    }

    // -- the states ----------------------------------------------------------

    @Test
    fun `a cancelled subscription keeps premium until the period it paid for runs out`() {
        // Cancelling turns auto-renewal off. It does not refund the current period, so
        // taking the app away on the spot would be charging for time nobody gets.
        googleSays(state = "SUBSCRIPTION_STATE_CANCELED", autoRenewing = false)

        billing.register(user, "token-cancelled")

        assertTrue(user.isPremium())
    }

    @Test
    fun `a subscription in its grace period keeps premium`() {
        googleSays(state = "SUBSCRIPTION_STATE_IN_GRACE_PERIOD")

        billing.register(user, "token-grace")

        assertTrue(user.isPremium())
    }

    @Test
    fun `an account on hold is not premium`() {
        googleSays(state = "SUBSCRIPTION_STATE_ON_HOLD")

        billing.register(user, "token-on-hold")

        assertFalse(user.isPremium())
    }

    @Test
    fun `a paused subscription is not premium`() {
        googleSays(state = "SUBSCRIPTION_STATE_PAUSED")

        billing.register(user, "token-paused")

        assertFalse(user.isPremium())
    }

    @Test
    fun `only the states that are actually paid for count as entitled`() {
        listOf(
            "SUBSCRIPTION_STATE_ACTIVE",
            "SUBSCRIPTION_STATE_CANCELED",
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"
        ).forEach { assertTrue(subscription(it).entitled, "$it should pay for premium") }

        listOf(
            "SUBSCRIPTION_STATE_PENDING",
            "SUBSCRIPTION_STATE_ON_HOLD",
            "SUBSCRIPTION_STATE_PAUSED",
            "SUBSCRIPTION_STATE_EXPIRED",
            "SUBSCRIPTION_STATE_UNSPECIFIED"
        ).forEach { assertFalse(subscription(it).entitled, "$it should not pay for premium") }
    }

    // -- what happens later ---------------------------------------------------

    @Test
    fun `a renewal picked up by the re-check extends the entitlement`() {
        googleSays(expiresInDays = 1)
        val purchase = billing.register(user, "token-renewing")

        googleSays(expiresInDays = 31, acknowledged = true)
        val changed = billing.recheck(purchase)

        assertTrue(changed, "the re-check should have noticed the new expiry")
        assertTrue(user.entitlementExpiresAt!!.isAfter(entitlements.now().plus(30, ChronoUnit.DAYS)))
    }

    @Test
    fun `an expired subscription loses premium on the next re-check`() {
        googleSays()
        val purchase = billing.register(user, "token-expiring")

        googleSays(state = "SUBSCRIPTION_STATE_EXPIRED", expiresInDays = -1, autoRenewing = false)
        billing.recheck(purchase)

        assertFalse(user.isPremium())
    }

    @Test
    fun `a token Google no longer recognises ends the entitlement rather than erroring`() {
        googleSays()
        val purchase = billing.register(user, "token-forgotten")

        google.failWith = InvalidPurchaseException("gone")
        billing.recheck(purchase)

        assertFalse(user.isPremium())
        assertEquals("SUBSCRIPTION_STATE_EXPIRED", purchase.state)
    }

    @Test
    fun `a Play lapse leaves a manual grant alone`() {
        entitlements.grant(user, null, EntitlementSource.MANUAL, "tester")
        googleSays(state = "SUBSCRIPTION_STATE_EXPIRED", expiresInDays = -1)

        billing.register(user, "token-lapsed")

        assertTrue(user.isPremium(), "the hand-made grant should have survived")
        assertEquals(EntitlementSource.MANUAL, user.entitlementSource)
    }

    @Test
    fun `Google being unreachable does not take premium away`() {
        googleSays()
        val purchase = billing.register(user, "token-outage")

        google.failWith = BillingUnavailableException("Google is down")
        assertThrows<BillingUnavailableException> { billing.recheck(purchase) }

        assertTrue(user.isPremium(), "an outage is not evidence that somebody stopped paying")
    }

    private fun subscription(state: String) = PlaySubscription(
        state = state,
        productId = "premium_monthly",
        expiresAt = Instant.now(),
        orderId = null,
        autoRenewing = false,
        acknowledged = true,
        test = false
    )
}
