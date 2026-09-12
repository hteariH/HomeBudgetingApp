package com.homebudget.app.service

import com.homebudget.app.controller.UserController
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.User
import com.homebudget.app.dto.CreateCategoryRequest
import com.homebudget.app.dto.SyncCategory
import com.homebudget.app.dto.SyncPayload
import com.homebudget.app.dto.SyncPushRequest
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.CurrencyRepository
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
class BudgetRolloverTest {

    @Autowired private lateinit var budgetService: BudgetService
    @Autowired private lateinit var categoryService: CategoryService
    @Autowired private lateinit var userController: UserController
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var categoryRepository: CategoryRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        val usd = currencyRepository.findByCode("USD")!!
        user = userRepository.save(
            User(
                email = "budget-test-${UUID.randomUUID()}@example.com",
                passwordHash = "x",
                firstName = "Budget",
                lastName = "Tester",
                role = Role.USER,
                baseCurrency = usd,
                budgetCycleStartDay = 1
            )
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `budgetCycleWindow calculates standard month when startDay is 1`() {
        val (start, end) = budgetService.budgetCycleWindow(2026, 5, 1)
        assertEquals(LocalDate.of(2026, 5, 1), start)
        assertEquals(LocalDate.of(2026, 5, 31), end)
    }

    @Test
    fun `budgetCycleWindow shifts window across month boundary when startDay is custom`() {
        val (start, end) = budgetService.budgetCycleWindow(2026, 5, 15)
        assertEquals(LocalDate.of(2026, 4, 15), start)
        assertEquals(LocalDate.of(2026, 5, 14), end)
    }

    @Test
    fun `budgetCycleWindow clamps startDay to valid bounds`() {
        val (start, end) = budgetService.budgetCycleWindow(2026, 5, 35)
        assertEquals(LocalDate.of(2026, 4, 28), start)
        assertEquals(LocalDate.of(2026, 5, 27), end)
    }

    @Test
    fun `calculateAvailableBudget rolls over positive surplus when enabled`() {
        val baseLimit = 50000L // $500.00
        val surplus = 12000L   // $120.00 under budget

        val withRollover = budgetService.calculateAvailableBudget(
            baseLimitMinor = baseLimit,
            previousPeriodUnspentMinor = surplus,
            rolloverEnabled = true,
            rolloverNegative = false
        )
        assertEquals(62000L, withRollover) // $620.00

        val withoutRollover = budgetService.calculateAvailableBudget(
            baseLimitMinor = baseLimit,
            previousPeriodUnspentMinor = surplus,
            rolloverEnabled = false,
            rolloverNegative = false
        )
        assertEquals(50000L, withoutRollover) // $500.00
    }

    @Test
    fun `calculateAvailableBudget rolls over negative deficit when enabled and caps at zero`() {
        val baseLimit = 50000L // $500.00
        val deficit = -15000L  // $150.00 over budget

        val withNegativeRollover = budgetService.calculateAvailableBudget(
            baseLimitMinor = baseLimit,
            previousPeriodUnspentMinor = deficit,
            rolloverEnabled = true,
            rolloverNegative = true
        )
        assertEquals(35000L, withNegativeRollover) // $350.00

        val withoutNegativeRollover = budgetService.calculateAvailableBudget(
            baseLimitMinor = baseLimit,
            previousPeriodUnspentMinor = deficit,
            rolloverEnabled = true,
            rolloverNegative = false
        )
        assertEquals(50000L, withoutNegativeRollover) // Capped at base limit

        // Deficit exceeding total budget is floored at 0
        val catastrophicDeficit = -80000L
        val floored = budgetService.calculateAvailableBudget(
            baseLimitMinor = baseLimit,
            previousPeriodUnspentMinor = catastrophicDeficit,
            rolloverEnabled = true,
            rolloverNegative = true
        )
        assertEquals(0L, floored)
    }

    @Test
    fun `user preferences update budgetCycleStartDay`() {
        val updated = userController.updatePreferences(budgetCycleStartDay = 15)
        assertEquals(15, updated.budgetCycleStartDay)
    }

    @Test
    fun `category creation and sync round-trip preserves rollover settings`() {
        val cat = categoryService.create(
            CreateCategoryRequest(
                name = "Utilities",
                type = CategoryType.EXPENSE,
                budgetLimit = BigDecimal("200.00"),
                rolloverEnabled = true,
                rolloverNegative = true
            )
        )
        assertTrue(cat.rolloverEnabled)
        assertTrue(cat.rolloverNegative)

        // Test sync round-trip
        val catUuid = UUID.randomUUID().toString()
        val pushRequest = SyncPushRequest(
            SyncPayload(
                categories = listOf(
                    SyncCategory(
                        uuid = catUuid,
                        name = "Dining Out",
                        type = CategoryType.EXPENSE,
                        budgetLimit = BigDecimal("300.00"),
                        rolloverEnabled = true,
                        rolloverNegative = false
                    )
                )
            )
        )

        val pushRes = syncService.push(pushRequest)
        assertEquals(1, pushRes.applied)

        val pullRes = syncService.pull(java.time.Instant.EPOCH, SyncService.MAX_PAGE)
        val pulled = pullRes.changes.categories.firstOrNull { it.uuid == catUuid }
        assertNotNull(pulled)
        assertTrue(pulled!!.rolloverEnabled)
        assertFalse(pulled.rolloverNegative)
    }
}
