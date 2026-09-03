package com.homebudget.app.service

import com.homebudget.app.domain.Entitlement
import com.homebudget.app.domain.PlanLimits
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.domain.isPremium
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.dto.SyncAccount
import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.dto.SyncPushRequest
import com.homebudget.app.dto.UpdateAccountRequest
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The free plan's account cap, on both doors into the table.
 *
 * The REST path is what the browser uses and the sync path is what a phone uses, and a cap
 * that only one of them honours is not a cap - so both are covered here, together with the
 * cases where it must *not* bite: an account that already exists, an archived one, and a
 * subscriber.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlanLimitTest {

    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var accountRepo: AccountRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    private lateinit var user: User
    private var usdId: Long = 0

    @BeforeEach
    fun signIn() {
        user = userRepository.save(
            User(
                email = "plan-${System.nanoTime()}@example.com",
                passwordHash = "x",
                firstName = "Test",
                lastName = "User",
                role = Role.USER,
                baseCurrency = currencyRepository.findByCode("USD")
            )
        )
        authenticate()
        usdId = currencyRepository.findByCode("USD")!!.id
    }

    @AfterEach
    fun signOut() = SecurityContextHolder.clearContext()

    private fun authenticate() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())
    }

    private fun subscribe(until: Instant? = null) {
        user.entitlement = Entitlement.PREMIUM
        user.entitlementExpiresAt = until
        user = userRepository.save(user)
        authenticate()
    }

    private fun addAccount(name: String) = accountService.create(
        CreateAccountRequest(name = name, initialBalance = BigDecimal.ZERO, currencyId = usdId)
    )

    private fun fillFreePlan() = (1..PlanLimits.FREE_ACCOUNTS).map { addAccount("Account $it") }

    // ─── the cap itself ──────────────────────────────────────────────────────

    @Test
    fun `a free account may hold exactly the free limit`() {
        fillFreePlan()

        assertEquals(
            PlanLimits.FREE_ACCOUNTS.toLong(),
            accountRepo.countByUserAndArchivedFalse(user),
        )
    }

    @Test
    fun `one more than the limit is refused, and says why`() {
        fillFreePlan()

        val refused = assertThrows<PlanLimitException> { addAccount("One too many") }

        assertTrue(
            refused.message!!.contains(PlanLimits.FREE_ACCOUNTS.toString()),
            "the message should say what the limit is, it read: ${refused.message}",
        )
    }

    @Test
    fun `a subscriber is not capped`() {
        fillFreePlan()
        subscribe()

        val extra = addAccount("Sixth")

        assertEquals("Sixth", extra.name)
    }

    @Test
    fun `a lapsed subscription is capped again`() {
        fillFreePlan()
        subscribe(until = Instant.now().minusSeconds(60))

        assertThrows<PlanLimitException> { addAccount("After expiry") }
    }

    // ─── where it must not bite ──────────────────────────────────────────────

    @Test
    fun `archiving frees a slot`() {
        val existing = fillFreePlan()
        accountService.update(existing.first().id, UpdateAccountRequest(archived = true))

        val replacement = addAccount("Replacement")

        assertEquals("Replacement", replacement.name)
    }

    @Test
    fun `accounts over the limit stay editable`() {
        subscribe()
        val over = (1..PlanLimits.FREE_ACCOUNTS + 2).map { addAccount("Account $it") }
        user.entitlement = Entitlement.FREE
        user = userRepository.save(user)
        authenticate()

        val renamed = accountService.update(over.last().id, UpdateAccountRequest(name = "Renamed"))

        assertEquals("Renamed", renamed.name)
    }

    // ─── the other door ──────────────────────────────────────────────────────

    @Test
    fun `sync refuses a new account past the limit`() {
        fillFreePlan()

        val result = syncService.push(SyncPushRequest(SyncPayload(accounts = listOf(syncAccount("Pushed")))))

        assertEquals(1, result.rejected.size, "the push should have been rejected")
        assertTrue(
            result.rejected.single().message.contains(PlanLimits.FREE_ACCOUNTS.toString()),
            "the rejection should say what the limit is, it read: ${result.rejected.single().message}",
        )
    }

    @Test
    fun `sync still accepts an edit to an account that already exists`() {
        val existing = fillFreePlan().first()

        val result = syncService.push(
            SyncPushRequest(
                SyncPayload(
                    accounts = listOf(
                        // `baseUpdatedAt` is the optimistic-concurrency token: it says which
                        // server version this edit was made against. Without it the server
                        // reads the push as a new record and rejects it as stale, which is
                        // the check working rather than the cap.
                        syncAccount("Renamed", uuid = existing.uuid, base = existing.updatedAt)
                    )
                )
            )
        )

        assertTrue(result.rejected.isEmpty(), "an existing account is not a new one: ${result.rejected}")
    }

    // ─── the clock, without a database ───────────────────────────────────────

    @Test
    fun `premium without an expiry does not lapse`() {
        val forever = user.copy(entitlement = Entitlement.PREMIUM, entitlementExpiresAt = null)

        assertTrue(forever.isPremium())
    }

    @Test
    fun `premium reads as free once the expiry has passed`() {
        val now = Instant.parse("2026-08-24T12:00:00Z")
        val lapsed = user.copy(
            entitlement = Entitlement.PREMIUM,
            entitlementExpiresAt = now.minusSeconds(1),
        )

        assertFalse(lapsed.isPremium(now))
        assertTrue(lapsed.copy(entitlementExpiresAt = now.plusSeconds(1)).isPremium(now))
    }

    private fun syncAccount(
        name: String,
        uuid: String = UUID.randomUUID().toString(),
        base: Instant? = null,
    ) = SyncAccount(
        uuid = uuid,
        name = name,
        currencyCode = "USD",
        updatedAt = Instant.now().plusSeconds(1),
        baseUpdatedAt = base,
    )
}
