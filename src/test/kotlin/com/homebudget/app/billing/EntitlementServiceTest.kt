package com.homebudget.app.billing

import com.homebudget.app.domain.Entitlement
import com.homebudget.app.domain.EntitlementSource
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.domain.isPremium
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The rules that keep the two ways of becoming a subscriber from undoing each other.
 *
 * Premium arrives either from a Play purchase or from an administrator's hand, and both
 * write the same two columns. What matters is that a lapsed subscription cannot cancel a
 * grant somebody made deliberately, and that neither source can shorten what the account
 * has already been promised.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntitlementServiceTest {

    @Autowired private lateinit var entitlements: EntitlementService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var currencies: CurrencyRepository

    private lateinit var user: User

    @BeforeEach
    fun newAccount() {
        user = users.save(
            User(
                email = "entitlement-${System.nanoTime()}@example.com",
                passwordHash = "x",
                firstName = "Test",
                lastName = "User",
                role = Role.USER,
                baseCurrency = currencies.findByCode("USD")
            )
        )
    }

    private fun inDays(days: Long): Instant = entitlements.now().plus(days, ChronoUnit.DAYS)

    // -- granting -------------------------------------------------------------

    @Test
    fun `a grant makes the account premium and records where it came from`() {
        val until = inDays(30)

        val granted = entitlements.grant(user, until, EntitlementSource.PLAY, "premium_monthly")

        assertTrue(granted.isPremium())
        assertEquals(until, granted.entitlementExpiresAt)
        assertEquals(EntitlementSource.PLAY, granted.entitlementSource)
    }

    @Test
    fun `a grant with no expiry does not lapse`() {
        val granted = entitlements.grant(user, null, EntitlementSource.MANUAL, "translator")

        assertTrue(granted.isPremium())
        assertNull(granted.entitlementExpiresAt)
    }

    @Test
    fun `a shorter grant does not cut an existing one short`() {
        entitlements.grant(user, inDays(365), EntitlementSource.MANUAL, "a year, by hand")

        val after = entitlements.grant(user, inDays(30), EntitlementSource.PLAY, "premium_monthly")

        assertEquals(EntitlementSource.MANUAL, after.entitlementSource, "the longer grant should have stood")
        assertTrue(after.entitlementExpiresAt!!.isAfter(inDays(300)))
    }

    @Test
    fun `no dated grant beats a standing one`() {
        entitlements.grant(user, null, EntitlementSource.MANUAL, "lifetime")

        val after = entitlements.grant(user, inDays(3650), EntitlementSource.PLAY, "premium_yearly")

        assertNull(after.entitlementExpiresAt, "a lifetime grant should not have gained an expiry")
    }

    @Test
    fun `a longer grant extends the entitlement`() {
        entitlements.grant(user, inDays(30), EntitlementSource.PLAY, "premium_monthly")

        val after = entitlements.grant(user, inDays(60), EntitlementSource.PLAY, "renewal")

        assertTrue(after.entitlementExpiresAt!!.isAfter(inDays(59)))
    }

    @Test
    fun `a lapsed entitlement is replaced rather than kept`() {
        // "Longer wins" must not mean "an expiry in the past beats a fresh purchase".
        user.entitlement = Entitlement.PREMIUM
        user.entitlementExpiresAt = entitlements.now().minus(1, ChronoUnit.DAYS)
        user.entitlementSource = EntitlementSource.PLAY
        user = users.save(user)

        val after = entitlements.grant(user, inDays(30), EntitlementSource.PLAY, "resubscribed")

        assertTrue(after.isPremium())
        assertTrue(after.entitlementExpiresAt!!.isAfter(entitlements.now()))
    }

    // -- revoking -------------------------------------------------------------

    @Test
    fun `a lapsed Play subscription does not touch a manual grant`() {
        entitlements.grant(user, null, EntitlementSource.MANUAL, "tester")

        val after = entitlements.revoke(user, EntitlementSource.PLAY, only = EntitlementSource.PLAY)

        assertTrue(after.isPremium(), "the manual grant should have survived the Play sweep")
    }

    @Test
    fun `a lapsed Play subscription ends a Play entitlement`() {
        entitlements.grant(user, inDays(30), EntitlementSource.PLAY, "premium_monthly")

        val after = entitlements.revoke(user, EntitlementSource.PLAY, only = EntitlementSource.PLAY)

        assertFalse(after.isPremium())
        assertEquals(EntitlementSource.NONE, after.entitlementSource)
        assertNull(after.entitlementExpiresAt, "revoking should leave no expiry lying about")
    }

    @Test
    fun `an administrator can revoke whatever granted it`() {
        entitlements.grant(user, inDays(30), EntitlementSource.PLAY, "premium_monthly")

        val after = entitlements.revoke(user, EntitlementSource.MANUAL)

        assertFalse(after.isPremium())
    }
}
