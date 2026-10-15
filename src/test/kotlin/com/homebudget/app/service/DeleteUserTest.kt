package com.homebudget.app.service

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateTransactionRequest
import com.homebudget.app.dto.SyncAccount
import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.dto.SyncPushRequest
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
}
