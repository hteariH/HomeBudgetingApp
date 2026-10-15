package com.homebudget.app.household

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.HouseholdRole
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.*
import com.homebudget.app.repository.*
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.SyncService
import com.homebudget.app.service.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Leaving, removing, handing over and renaming: a household always has exactly one owner. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestIdentity::class)
class MembershipTest {

    @Autowired lateinit var identity: TestIdentity
    @Autowired lateinit var membershipService: MembershipService
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var syncService: SyncService
    @Autowired lateinit var householdRepository: HouseholdRepository
    @Autowired lateinit var householdMemberRepository: HouseholdMemberRepository
    @Autowired lateinit var categoryRepository: CategoryRepository
    @Autowired lateinit var transactionRepository: TransactionRepository
    @Autowired lateinit var currencyRepository: CurrencyRepository
    @Autowired lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun uuid() = UUID.randomUUID().toString()

    private fun membershipOf(who: UserPrincipal) = householdMemberRepository.findFirstByUserId(who.user.id)!!

    private fun count(sql: String, id: Long) = jdbc.queryForObject(sql, Long::class.java, id)

    @Test
    fun `a member who leaves gets an empty household of their own and their transactions stay`() {
        val anna = identity.newMember("anna", "EUR")
        val boris = identity.joinAs("boris", anna.household)
        val account = uuid()
        val tx = uuid()
        identity.signIn(boris)
        val pushed = syncService.push(SyncPushRequest(SyncPayload(
            accounts = listOf(SyncAccount(uuid = account, name = "Family cash", currencyCode = "EUR")),
            transactions = listOf(SyncTransaction(
                uuid = tx, accountUuid = account, amount = BigDecimal("12.50"), currencyCode = "EUR",
                accountAmount = BigDecimal("12.50"), type = TransactionType.EXPENSE,
                occurredAt = Instant.parse("2026-10-01T10:00:00Z")
            ))
        )))
        assertTrue(pushed.rejected.isEmpty(), "setup push was rejected: ${pushed.rejected}")

        membershipService.leave()

        val membership = membershipOf(boris)
        assertEquals(HouseholdRole.OWNER, membership.role)
        assertNotEquals(anna.household.id, membership.household.id)
        assertEquals("EUR", membership.household.baseCurrency?.code)
        assertEquals(
            setOf("Salary", "Freelance"),
            categoryRepository.findByHouseholdAndType(membership.household, CategoryType.INCOME).map { it.name }.toSet()
        )
        assertEquals(8L, count("SELECT COUNT(*) FROM categories WHERE household_id = ?", membership.household.id))
        assertEquals(0L, count("SELECT COUNT(*) FROM accounts WHERE household_id = ?", membership.household.id))

        val kept = transactionRepository.findByUuidAndHousehold(tx, anna.household)!!
        assertEquals(boris.user.id, kept.createdBy?.id)
        assertEquals(1L, householdMemberRepository.countByHouseholdId(anna.household.id))
    }

    @Test
    fun `an owner cannot leave while others remain`() {
        val anna = identity.newMember("anna")
        identity.joinAs("boris", anna.household)
        identity.signIn(anna)
        assertThrows<IllegalArgumentException> { membershipService.leave() }
        assertEquals(HouseholdRole.OWNER, membershipOf(anna).role)
    }

    @Test
    fun `someone alone in their household cannot leave it`() {
        val anna = identity.newMember("anna")
        identity.signIn(anna)
        assertThrows<IllegalArgumentException> { membershipService.leave() }
    }

    @Test
    fun `the owner removes a member`() {
        val anna = identity.newMember("anna")
        val boris = identity.joinAs("boris", anna.household)
        val stranger = identity.newMember("stranger")

        identity.signIn(boris)
        assertThrows<AccessDeniedException> { membershipService.remove(anna.user.id) }

        identity.signIn(anna)
        assertThrows<IllegalArgumentException> { membershipService.remove(anna.user.id) }
        assertThrows<NoSuchElementException> { membershipService.remove(stranger.user.id) }
        membershipService.remove(boris.user.id)

        val membership = membershipOf(boris)
        assertEquals(HouseholdRole.OWNER, membership.role)
        assertNotEquals(anna.household.id, membership.household.id)
        assertEquals(
            listOf(anna.user.id),
            householdMemberRepository.findAllByHouseholdId(anna.household.id).map { it.user.id }
        )
    }

    @Test
    fun `ownership moves`() {
        val anna = identity.newMember("anna")
        val boris = identity.joinAs("boris", anna.household)

        identity.signIn(boris)
        assertThrows<AccessDeniedException> { membershipService.transferOwnership(boris.user.id) }

        identity.signIn(anna)
        assertThrows<IllegalArgumentException> { membershipService.transferOwnership(anna.user.id) }
        membershipService.transferOwnership(boris.user.id)

        assertEquals(HouseholdRole.OWNER, membershipOf(boris).role)
        assertEquals(HouseholdRole.MEMBER, membershipOf(anna).role)
        membershipService.leave()
        assertNotEquals(anna.household.id, membershipOf(anna).household.id)
    }

    @Test
    fun `only the owner renames and changes the base currency`() {
        val anna = identity.newMember("anna")
        val boris = identity.joinAs("boris", anna.household)
        val eur = currencyRepository.findByCode("EUR")!!

        identity.signIn(boris)
        assertThrows<AccessDeniedException> { membershipService.rename("x") }
        assertThrows<AccessDeniedException> { userService.updateBaseCurrency(eur.id) }

        identity.signIn(anna)
        assertThrows<IllegalArgumentException> { membershipService.rename("  ") }
        membershipService.rename("  The Annas ")
        assertEquals("The Annas", householdRepository.findById(anna.household.id).orElseThrow().name)
        userService.updateBaseCurrency(eur.id)
        assertEquals("EUR", householdRepository.findById(anna.household.id).orElseThrow().baseCurrency?.code)
    }

    @Test
    fun `deleting an owner hands the household to the longest-standing member`() {
        val anna = identity.newMember("anna")
        val boris = identity.joinAs("boris", anna.household)
        val clara = identity.joinAs("clara", anna.household)
        identity.signIn(anna)
        val pushed = syncService.push(SyncPushRequest(SyncPayload(
            accounts = listOf(SyncAccount(uuid = uuid(), name = "Family cash", currencyCode = "USD"))
        )))
        assertTrue(pushed.rejected.isEmpty(), "setup push was rejected: ${pushed.rejected}")

        userService.deleteUser(anna.user)

        assertEquals(HouseholdRole.OWNER, membershipOf(boris).role)
        assertEquals(HouseholdRole.MEMBER, membershipOf(clara).role)
        assertTrue(householdRepository.existsById(anna.household.id))
        assertEquals(1L, count("SELECT COUNT(*) FROM accounts WHERE household_id = ?", anna.household.id))
    }
}
