package com.homebudget.app.service

import com.homebudget.app.TestIdentity
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateAccountRequest
import com.homebudget.app.dto.CreateCategoryRequest
import com.homebudget.app.dto.CreateTransactionRequest
import com.homebudget.app.dto.CreateTransferRequest
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.TransactionRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Covers the two structural claims behind the schema change: balances are derived
 * from the ledger, and a transfer is a pair of rows that must move together.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(TestIdentity::class)
class LedgerBalanceTest {

    @Autowired private lateinit var accountService: AccountService
    @Autowired private lateinit var categoryService: CategoryService
    @Autowired private lateinit var transactionService: TransactionService
    @Autowired private lateinit var transferService: TransferService
    @Autowired private lateinit var transactionRepo: TransactionRepository
    @Autowired private lateinit var identity: TestIdentity
    @Autowired private lateinit var currencyRepository: CurrencyRepository

    private var usdId: Long = 0
    private var jpyId: Long = 0

    @BeforeEach
    fun signIn() {
        identity.signIn(identity.newMember("ledger"))

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

    private fun expenseCategory() =
        categoryService.create(CreateCategoryRequest(name = "Food", type = CategoryType.EXPENSE))

    @Test
    fun `balance is the opening figure plus the signed ledger`() {
        val account = newAccount("Cash", usdId, "100.00")
        val category = expenseCategory()

        transactionService.create(
            CreateTransactionRequest(
                accountId = account.id,
                categoryId = category.id,
                amount = BigDecimal("30.00"),
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 4, 1)
            )
        )
        transactionService.create(
            CreateTransactionRequest(
                accountId = account.id,
                categoryId = null,
                amount = BigDecimal("5.50"),
                type = TransactionType.INCOME,
                date = LocalDate.of(2026, 4, 2)
            )
        )

        // 100.00 - 30.00 + 5.50
        assertEquals(7550L, accountService.balanceOf(account.id))

        val filtered = transactionService.findFiltered(
            accountId = null,
            categoryId = null,
            type = null,
            dateFrom = null,
            dateTo = null,
            pageable = org.springframework.data.domain.PageRequest.of(0, 10)
        )
        assertEquals(2, filtered.totalElements)
    }

    @Test
    fun `a zero-decimal currency is not scaled as if it had cents`() {
        val account = newAccount("Tokyo", jpyId, "1000")
        transactionService.create(
            CreateTransactionRequest(
                accountId = account.id,
                categoryId = null,
                amount = BigDecimal("250"),
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 4, 1)
            )
        )
        // 750 yen, not 75000 - JPY has no minor unit.
        assertEquals(750L, accountService.balanceOf(account.id))
    }

    @Test
    fun `deleting and re-creating a transaction leaves the balance where it started`() {
        // This is the property a stored running balance did not have: replaying a
        // write used to apply its delta twice.
        val account = newAccount("Cash", usdId, "100.00")
        val opening = accountService.balanceOf(account.id)

        val tx = transactionService.create(
            CreateTransactionRequest(
                accountId = account.id,
                categoryId = null,
                amount = BigDecimal("42.00"),
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 4, 1)
            )
        )
        assertEquals(opening - 4200L, accountService.balanceOf(account.id))

        transactionService.delete(tx.id)
        assertEquals(opening, accountService.balanceOf(account.id))

        transactionService.create(
            CreateTransactionRequest(
                accountId = account.id,
                categoryId = null,
                amount = BigDecimal("42.00"),
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 4, 1)
            )
        )
        assertEquals(opening - 4200L, accountService.balanceOf(account.id))
    }

    @Test
    fun `a transfer writes two legs and moves money between accounts`() {
        val from = newAccount("Checking", usdId, "500.00")
        val to = newAccount("Savings", usdId, "0.00")

        val debit = transferService.create(
            CreateTransferRequest(
                fromAccountId = from.id,
                toAccountId = to.id,
                fromAmount = BigDecimal("120.00"),
                toAmount = BigDecimal("120.00"),
                date = LocalDate.of(2026, 4, 3)
            )
        )

        assertEquals(38000L, accountService.balanceOf(from.id))
        assertEquals(12000L, accountService.balanceOf(to.id))

        val legs = transactionRepo.findAllByTransferGroupIdAndHousehold(debit.transferGroupId!!, debit.household)
        assertEquals(2, legs.size, "a transfer is exactly two rows")
        assertEquals(setOf(TransactionType.TRANSFER), legs.map { it.type }.toSet())
        // Neither leg belongs to a category.
        legs.forEach { assertNull(it.category) }
    }

    @Test
    fun `a cross-currency transfer records each leg in its own currency`() {
        val usd = newAccount("USD wallet", usdId, "1000.00")
        val jpy = newAccount("JPY wallet", jpyId, "0")

        transferService.create(
            CreateTransferRequest(
                fromAccountId = usd.id,
                toAccountId = jpy.id,
                fromAmount = BigDecimal("100.00"),
                toAmount = BigDecimal("15000"),
                date = LocalDate.of(2026, 4, 3)
            )
        )

        assertEquals(90000L, accountService.balanceOf(usd.id))   // 900.00 USD
        assertEquals(15000L, accountService.balanceOf(jpy.id))   // 15000 JPY, unscaled
    }

    @Test
    fun `deleting either leg of a transfer removes both`() {
        val from = newAccount("Checking", usdId, "500.00")
        val to = newAccount("Savings", usdId, "0.00")
        val debit = transferService.create(
            CreateTransferRequest(
                fromAccountId = from.id,
                toAccountId = to.id,
                fromAmount = BigDecimal("120.00"),
                toAmount = BigDecimal("120.00"),
                date = LocalDate.of(2026, 4, 3)
            )
        )
        val credit = transferService.creditLegOf(debit)

        // Deleting through the plain transaction endpoint must not strand the other leg.
        transactionService.delete(credit.id)

        assertEquals(50000L, accountService.balanceOf(from.id))
        assertEquals(0L, accountService.balanceOf(to.id))
        assertEquals(
            0,
            transactionRepo.findAllByTransferGroupIdAndHousehold(debit.transferGroupId!!, debit.household).size
        )
    }

    @Test
    fun `transfers cannot be created through the plain transaction endpoint`() {
        val account = newAccount("Cash", usdId, "10.00")
        assertThrows<IllegalArgumentException> {
            transactionService.create(
                CreateTransactionRequest(
                    accountId = account.id,
                    categoryId = null,
                    amount = BigDecimal("1.00"),
                    type = TransactionType.TRANSFER,
                    date = LocalDate.of(2026, 4, 1)
                )
            )
        }
    }

    @Test
    fun `an expense cannot be filed under an income category`() {
        val account = newAccount("Cash", usdId, "10.00")
        val income = categoryService.create(
            CreateCategoryRequest(name = "Salary", type = CategoryType.INCOME)
        )
        assertThrows<IllegalArgumentException> {
            transactionService.create(
                CreateTransactionRequest(
                    accountId = account.id,
                    categoryId = income.id,
                    amount = BigDecimal("1.00"),
                    type = TransactionType.EXPENSE,
                    date = LocalDate.of(2026, 4, 1)
                )
            )
        }
    }
}
