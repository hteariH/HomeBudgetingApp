package com.homebudget.app.bank

import com.homebudget.app.domain.ExchangeRate
import com.homebudget.app.domain.RateSource
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.domain.User
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.ExchangeRateRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.UserPrincipal
import com.homebudget.app.service.AccountService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Importing a statement line as a transfer to another account rather than as spending.
 *
 * The claim being tested is the one that makes the option worth having: a line that only
 * moved money between the user's own accounts must leave the total across them unchanged,
 * and must not land in a category where it would read as expenditure.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportAsTransferTest {

    @Autowired private lateinit var importService: BankConnectionService
    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var transactionRepo: TransactionRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var currencyRepository: CurrencyRepository
    @Autowired private lateinit var rateRepository: ExchangeRateRepository

    private var usdId: Long = 0
    private var jpyId: Long = 0

    @BeforeEach
    fun signIn() {
        val user = userRepository.save(
            User(
                email = "import-transfer-${System.nanoTime()}@example.com",
                passwordHash = "x",
                firstName = "Test",
                lastName = "User",
                role = Role.USER,
                baseCurrency = currencyRepository.findByCode("USD")
            )
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UserPrincipal(user), null, emptyList())

        usdId = currencyRepository.findByCode("USD")!!.id
        jpyId = currencyRepository.findByCode("JPY")!!.id
    }

    @AfterEach
    fun signOut() = SecurityContextHolder.clearContext()

    private fun newAccount(name: String, currencyId: Long, opening: String) =
        accountService.create(
            CreateAccountRequest(
                name = name,
                initialBalance = BigDecimal(opening),
                currencyId = currencyId
            )
        )

    private fun row(externalId: String, amountMinor: Long, type: TransactionType) =
        BankConnectionService.ImportableRow(
            externalId = externalId,
            occurredAt = Instant.parse("2026-03-01T10:00:00Z"),
            description = "To savings",
            amountMinor = amountMinor,
            type = type
        )

    @Test
    fun `an outgoing row imported as a transfer moves money instead of spending it`() {
        val current = newAccount("Current", usdId, "1000.00")
        val savings = newAccount("Savings", usdId, "0.00")

        val result = importService.importRows(
            accountId = current.id,
            rows = listOf(row("tx-1", 25_000, TransactionType.EXPENSE)),
            categoryByExternalId = emptyMap(),
            transferAccountByExternalId = mapOf("tx-1" to savings.id)
        )

        assertEquals(1, result.imported)
        assertEquals(750_00L, accountService.balanceOf(current.id))
        assertEquals(250_00L, accountService.balanceOf(savings.id))

        val legs = transactionRepo.findAll().filter { it.type == TransactionType.TRANSFER }
        assertEquals(2, legs.size)
        assertEquals(1, legs.map { it.transferGroupId }.distinct().size)
        // Nothing categorised: a transfer is not spending, and a category would make it read as some.
        assertTrue(legs.all { it.category == null })
        assertEquals(-250_00L, legs.first { it.account.id == current.id }.accountAmountMinor)
        assertEquals(250_00L, legs.first { it.account.id == savings.id }.accountAmountMinor)
        // Only the statement's own side carries the id it was deduplicated by.
        assertEquals("tx-1", legs.first { it.account.id == current.id }.externalId)
        assertNull(legs.first { it.account.id == savings.id }.externalId)
    }

    @Test
    fun `an incoming row imported as a transfer credits the statement's account`() {
        val current = newAccount("Current", usdId, "0.00")
        val savings = newAccount("Savings", usdId, "1000.00")

        importService.importRows(
            accountId = current.id,
            rows = listOf(row("tx-2", 40_000, TransactionType.INCOME)),
            categoryByExternalId = emptyMap(),
            transferAccountByExternalId = mapOf("tx-2" to savings.id)
        )

        assertEquals(400_00L, accountService.balanceOf(current.id))
        assertEquals(600_00L, accountService.balanceOf(savings.id))
    }

    @Test
    fun `a cross-currency transfer converts the far leg at the stored rate`() {
        val usd = newAccount("Dollars", usdId, "1000.00")
        val yen = newAccount("Yen", jpyId, "0")
        rateRepository.save(
            ExchangeRate(
                baseCode = "USD",
                quoteCode = "JPY",
                rate = BigDecimal("150"),
                rateDate = LocalDate.of(2026, 3, 1),
                source = RateSource.FRANKFURTER
            )
        )

        importService.importRows(
            accountId = usd.id,
            rows = listOf(row("tx-3", 10_000, TransactionType.EXPENSE)),
            categoryByExternalId = emptyMap(),
            transferAccountByExternalId = mapOf("tx-3" to yen.id)
        )

        assertEquals(900_00L, accountService.balanceOf(usd.id))
        // JPY has no minor unit, so 100 USD at 150 is 15000 yen, not 15000 hundredths.
        assertEquals(15_000L, accountService.balanceOf(yen.id))
    }

    @Test
    fun `a row named by neither map is left alone`() {
        val current = newAccount("Current", usdId, "1000.00")
        val savings = newAccount("Savings", usdId, "0.00")

        val result = importService.importRows(
            accountId = current.id,
            rows = listOf(row("tx-4", 25_000, TransactionType.EXPENSE)),
            categoryByExternalId = emptyMap(),
            transferAccountByExternalId = mapOf("other-row" to savings.id)
        )

        assertEquals(0, result.imported)
        assertEquals(1000_00L, accountService.balanceOf(current.id))
    }

    @Test
    fun `a transfer to a missing account is skipped rather than half written`() {
        val current = newAccount("Current", usdId, "1000.00")

        val result = importService.importRows(
            accountId = current.id,
            rows = listOf(row("tx-5", 25_000, TransactionType.EXPENSE)),
            categoryByExternalId = emptyMap(),
            transferAccountByExternalId = mapOf("tx-5" to 999_999L)
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(1000_00L, accountService.balanceOf(current.id))
        assertTrue(transactionRepo.findAll().none { it.type == TransactionType.TRANSFER })
    }
}
