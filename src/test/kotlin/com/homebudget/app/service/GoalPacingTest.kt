package com.homebudget.app.service

import com.homebudget.app.controller.CreateGoalRequest
import com.homebudget.app.controller.GoalController
import com.homebudget.app.controller.UpdateGoalRequest
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.SyncGoal
import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.dto.SyncPushRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.GoalRepository
import com.homebudget.app.repository.UserRepository
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
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class GoalPacingTest {

    @Autowired private lateinit var goalController: GoalController
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var goalRepository: GoalRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        val usd = currencyRepository.findByCode("USD")!!
        user = userRepository.save(
            User(
                email = "goal-test-${UUID.randomUUID()}@example.com",
                passwordHash = "x",
                firstName = "Goal",
                lastName = "Tester",
                role = Role.USER,
                baseCurrency = usd
            )
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `create goal calculates progress and monthly pacing for future target date`() {
        val targetDate = LocalDate.now().plusMonths(6)
        val request = CreateGoalRequest(
            name = "Emergency Fund",
            targetAmount = BigDecimal("6000.00"),
            currencyCode = "USD",
            currentAmount = BigDecimal("1200.00"),
            targetDate = targetDate
        )

        val response = goalController.create(request)
        assertEquals("Emergency Fund", response.name)
        assertEquals(BigDecimal("6000.00"), response.targetAmount)
        assertEquals(BigDecimal("1200.00"), response.currentAmount)

        val pacing = response.pacing
        assertEquals(20.0, pacing.progressPercent, 0.01)
        assertEquals(BigDecimal("4800.00"), pacing.remainingAmount)
        assertTrue(pacing.monthsRemaining >= 5)
        assertNotNull(pacing.monthlyPacing)
        assertTrue(pacing.monthlyPacing!! > BigDecimal.ZERO)
    }

    @Test
    fun `update goal when target reached caps progress and clears monthly pacing`() {
        val targetDate = LocalDate.now().plusMonths(3)
        val created = goalController.create(
            CreateGoalRequest(
                name = "Vacation",
                targetAmount = BigDecimal("2000.00"),
                currencyCode = "USD",
                currentAmount = BigDecimal("500.00"),
                targetDate = targetDate
            )
        )

        val updated = goalController.update(
            created.id,
            UpdateGoalRequest(currentAmount = BigDecimal("2000.00"))
        )

        assertEquals(100.0, updated.pacing.progressPercent)
        assertEquals(BigDecimal("0.00"), updated.pacing.remainingAmount)
        assertNull(updated.pacing.monthlyPacing)
    }

    @Test
    fun `sync push and pull round-trip for savings goals`() {
        val goalUuid = UUID.randomUUID().toString()
        val targetDate = LocalDate.now().plusMonths(12)

        val pushRequest = SyncPushRequest(
            SyncPayload(
                goals = listOf(
                    SyncGoal(
                        uuid = goalUuid,
                        name = "New Car",
                        targetAmount = BigDecimal("15000.00"),
                        currencyCode = "USD",
                        currentAmount = BigDecimal("3000.00"),
                        targetDate = targetDate,
                        icon = "car",
                        color = "#3B82F6"
                    )
                )
            )
        )

        val pushResponse = syncService.push(pushRequest)
        assertEquals(1, pushResponse.applied)
        assertEquals(0, pushResponse.rejected.size)

        val pullResponse = syncService.pull(java.time.Instant.EPOCH, SyncService.MAX_PAGE)
        val pulled = pullResponse.changes.goals.firstOrNull { it.uuid == goalUuid }
        assertNotNull(pulled)
        assertEquals("New Car", pulled!!.name)
        assertEquals(0, pulled.targetAmount.compareTo(BigDecimal("15000.00")))
        assertEquals(0, pulled.currentAmount.compareTo(BigDecimal("3000.00")))
        assertEquals("car", pulled.icon)
    }
}
