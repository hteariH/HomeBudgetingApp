package com.homebudget.app.service

import com.homebudget.app.domain.Household
import com.homebudget.app.dto.RegisterRequest
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.HouseholdRepository
import com.homebudget.app.repository.SubscriptionRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.SecurityUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * A login without a household cannot sign in, so registration must be all or nothing:
 * when anything after the user row fails, the user row goes too.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:registration-atomicity;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "app.instance.registration=open"
])
class RegistrationAtomicityTest {

    /** Seeding fails after the user and the household have already been written. */
    @TestConfiguration
    class FailingSeeding {
        @Bean
        @Primary
        fun failingCategoryService(
            repo: CategoryRepository,
            transactions: TransactionRepository,
            subscriptions: SubscriptionRepository,
            securityUtils: SecurityUtils
        ): CategoryService = FailingCategoryService(repo, transactions, subscriptions, securityUtils)

        // Open, and named: the inherited @Transactional makes Spring proxy it by subclassing.
        open class FailingCategoryService(
            repo: CategoryRepository,
            transactions: TransactionRepository,
            subscriptions: SubscriptionRepository,
            securityUtils: SecurityUtils
        ) : CategoryService(repo, transactions, subscriptions, securityUtils) {
            override fun seedDefaultCategories(household: Household) {
                throw IllegalStateException("seeding failed")
            }
        }
    }

    @Autowired private lateinit var registration: RegistrationService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var households: HouseholdRepository

    @Test
    fun `a failure after the user is saved leaves no user and no household behind`() {
        val request = RegisterRequest(
            email = "half@example.com",
            password = "a-long-enough-password",
            firstName = "Half",
            lastName = "Done"
        )
        assertThrows(IllegalStateException::class.java) { registration.register(request) }

        assertFalse(users.existsByEmail("half@example.com"), "the user row must roll back")
        assertEquals(0L, households.count(), "the household must roll back")
        assertEquals(true, registration.setupRequired(), "the instance must still be fresh")
    }
}
