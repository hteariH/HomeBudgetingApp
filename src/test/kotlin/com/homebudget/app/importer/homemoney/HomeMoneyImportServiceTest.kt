package com.homebudget.app.importer.homemoney

import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.Role
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.domain.User
import com.homebudget.app.repository.AccountRepository
import com.homebudget.app.repository.CategoryRepository
import com.homebudget.app.repository.CurrencyRepository
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * The import writing into a real ledger.
 *
 * Nothing here talks to homemoney.ua: the operations are handed in directly, which is the
 * only way to test the half that matters - what ends up in the database. The claims under
 * test are the ones a migration lives or dies by: the balances agree with HomeMoney's
 * afterwards, a transfer is one transfer rather than two unrelated entries, and running
 * the whole thing twice changes nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HomeMoneyImportServiceTest {

    @Autowired private lateinit var service: HomeMoneyImportService
    @Autowired private lateinit var userRepo: UserRepository
    @Autowired private lateinit var accountRepo: AccountRepository
    @Autowired private lateinit var categoryRepo: CategoryRepository
    @Autowired private lateinit var transactionRepo: TransactionRepository
    @Autowired private lateinit var currencyRepo: CurrencyRepository

    private var userId: Long = 0

    private val uahAccount = "homemoney:1:980"
    private val usdAccount = "homemoney:2:840"

    @BeforeEach
    fun createUser() {
        userId = userRepo.save(
            User(
                email = "homemoney-${System.nanoTime()}@example.com",
                passwordHash = "x",
                firstName = "Test",
                lastName = "User",
                role = Role.USER,
                baseCurrency = currencyRepo.findByCode("UAH")
            )
        ).id
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    /** Two accounts, two spendable categories, and a transfer category to be dropped. */
    private fun plan(
        uahBalance: String = "4000.00",
        usdBalance: String = "100.00"
    ) = HomeMoneyPlan(
        accounts = listOf(
            PlannedAccount(
                externalId = uahAccount,
                homeMoneyAccountId = "1",
                homeMoneyCurrencyId = "980",
                name = "Wallet",
                currencyLabel = "UAH",
                currencyCode = "UAH",
                balance = BigDecimal(uahBalance)
            ),
            PlannedAccount(
                externalId = usdAccount,
                homeMoneyAccountId = "2",
                homeMoneyCurrencyId = "840",
                name = "Savings",
                currencyLabel = "USD",
                currencyCode = "USD",
                balance = BigDecimal(usdBalance)
            )
        ),
        categories = listOf(
            PlannedCategory("homemoney:10", "Groceries", "Food / Groceries", CategoryType.EXPENSE, null, false),
            PlannedCategory("homemoney:11", "Salary", "Salary", CategoryType.INCOME, null, false)
        )
    )

    private fun operations() = listOf(
        // -150.50 UAH, categorised
        HomeMoneyTransaction(
            guid = "g-expense", type = HomeMoneyKind.EXPENSE, total = BigDecimal("-150.50"),
            accountId = "1", currencyId = "980", categoryId = "10",
            dateUnix = 1_700_000_000, description = "Silpo"
        ),
        // +5000 UAH
        HomeMoneyTransaction(
            guid = "g-income", type = HomeMoneyKind.INCOME, total = BigDecimal("5000.00"),
            accountId = "1", currencyId = "980", categoryId = "11",
            dateUnix = 1_700_100_000, description = "March"
        ),
        // 1000 UAH out, 24.50 USD in
        HomeMoneyTransaction(
            guid = "g-transfer", type = HomeMoneyKind.TRANSFER, total = BigDecimal("1000.00"),
            accountId = "1", currencyId = "980",
            transAccountId = "2", transCurrencyId = "840", transTotal = BigDecimal("24.50"),
            dateUnix = 1_700_200_000
        ),
        // Neither of these belongs in a ledger.
        HomeMoneyTransaction(
            guid = "g-planned", type = HomeMoneyKind.EXPENSE, total = BigDecimal("99.00"),
            accountId = "1", currencyId = "980", dateUnix = 1_700_300_000, plan = true
        ),
        HomeMoneyTransaction(
            guid = "g-deleted", type = HomeMoneyKind.EXPENSE, total = BigDecimal("77.00"),
            accountId = "1", currencyId = "980", dateUnix = 1_700_400_000, deleted = true
        )
    )

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    fun `preparing creates the accounts and the spendable categories`() {
        val targets = service.prepare(userId, plan())

        assertEquals(2, targets.accountsCreated)
        assertEquals(2, targets.categoriesCreated)
        assertEquals(setOf(uahAccount, usdAccount), targets.accounts.keys)

        val created = accountRepo.findAllByUser(user()).associateBy { it.externalAccountId }
        assertEquals("UAH", created[uahAccount]!!.currency.code)
        assertEquals("USD", created[usdAccount]!!.currency.code)
        // The opening figure is not known yet - it falls out of the ledger afterwards.
        assertEquals(0L, created[uahAccount]!!.initialBalanceMinor)
    }

    @Test
    fun `a scheduled and a deleted operation are counted as skipped, not imported`() {
        val targets = service.prepare(userId, plan())

        val result = service.importChunk(userId, targets, operations())

        assertEquals(3, result.imported, "expense, income and one transfer")
        assertEquals(2, result.skipped, "the scheduled one and the deleted one")
    }

    @Test
    fun `a transfer becomes one pair of rows, signed, and not two unrelated entries`() {
        val targets = service.prepare(userId, plan())
        service.importChunk(userId, targets, operations())

        val legs = ledger().filter { it.type == TransactionType.TRANSFER }
        assertEquals(2, legs.size)
        assertEquals(1, legs.map { it.transferGroupId }.distinct().size, "both legs share one group")

        val debit = legs.first { it.accountAmountMinor < 0 }
        val credit = legs.first { it.accountAmountMinor > 0 }
        assertEquals(-100_000L, debit.accountAmountMinor, "1000.00 UAH out")
        assertEquals(2_450L, credit.accountAmountMinor, "24.50 USD in")
        assertEquals(credit.account.id, debit.transferCounterpartAccountId)
        assertEquals(debit.account.id, credit.transferCounterpartAccountId)
        // Transfers have no category on our side; a category here would be a lie about
        // where the money went.
        assertNull(debit.category)
    }

    @Test
    fun `balances agree with HomeMoney once the opening figures are reconciled`() {
        val targets = service.prepare(userId, plan(uahBalance = "4000.00", usdBalance = "100.00"))
        service.importChunk(userId, targets, operations())

        service.reconcileOpeningBalances(userId, targets)

        val balances = accountRepo.balancesOf(user()).associate { it.accountId to it.balanceMinor }
        val byExternal = accountRepo.findAllByUser(user()).associateBy { it.externalAccountId }
        // This is the "import the state" half of the feature: our balance is derived, so
        // the opening figure is the only lever, and it is set to whatever the imported
        // history does not already account for.
        assertEquals(400_000L, balances[byExternal[uahAccount]!!.id])
        assertEquals(10_000L, balances[byExternal[usdAccount]!!.id])
        // UAH ledger is -150.50 + 5000 - 1000 = 3849.50, so the account opened at 150.50.
        assertEquals(15_050L, byExternal[uahAccount]!!.initialBalanceMinor)
    }

    @Test
    fun `running the import a second time changes nothing`() {
        val targets = service.prepare(userId, plan())
        service.importChunk(userId, targets, operations())

        val second = service.importChunk(userId, targets, operations())

        assertEquals(0, second.imported)
        assertEquals(5, second.skipped, "every operation is either already in or not a ledger entry")
        assertEquals(4, ledger().size, "two singles plus one transfer pair")
    }

    @Test
    fun `an account the user already has keeps its own opening balance`() {
        val existing = accountRepo.save(
            com.homebudget.app.domain.Account(
                name = "My own wallet",
                user = user(),
                currency = currencyRepo.findByCode("UAH")!!,
                initialBalanceMinor = 999_99L
            )
        ).id

        val mapped = plan().let { p ->
            p.copy(accounts = listOf(p.accounts[0].copy(targetAccountId = existing), p.accounts[1]))
        }
        val targets = service.prepare(userId, mapped)
        service.importChunk(userId, targets, operations())
        service.reconcileOpeningBalances(userId, targets)

        // Rewriting this would silently overwrite a figure the user typed themselves.
        assertEquals(999_99L, accountRepo.findByIdAndUser(existing, user())!!.initialBalanceMinor)
    }

    @Test
    fun `an operation on an account left out of the plan is skipped`() {
        val onlyUah = plan().let { it.copy(accounts = listOf(it.accounts[0], it.accounts[1].copy(included = false))) }
        val targets = service.prepare(userId, onlyUah)

        val result = service.importChunk(userId, targets, operations())

        // The transfer goes with it: one leg without its counterpart is a wrong balance
        // on the leg that did land.
        assertEquals(2, result.imported, "the expense and the income")
        assertEquals(3, result.skipped, "the transfer, the scheduled one and the deleted one")
    }

    @Test
    fun `an unresolved currency is refused rather than guessed at`() {
        val unresolved = plan().let { p ->
            p.copy(accounts = listOf(p.accounts[0].copy(currencyCode = null, currencyLabel = "???")))
        }

        val thrown = assertThrows<IllegalArgumentException> { service.prepare(userId, unresolved) }
        assertEquals(true, thrown.message!!.contains("???"))
    }

    @Test
    fun `a category of the wrong direction is not attached to an operation`() {
        val swapped = plan().let { p ->
            p.copy(
                categories = listOf(
                    // HomeMoney says this is where the grocery expense goes; here it is
                    // an income category, which cannot hold one.
                    PlannedCategory("homemoney:10", "Groceries", "Groceries", CategoryType.INCOME, null, false)
                )
            )
        }
        val targets = service.prepare(userId, swapped)
        service.importChunk(userId, targets, operations().take(1))

        val imported = ledger().single()
        assertEquals(TransactionType.EXPENSE, imported.type)
        assertNull(imported.category, "better uncategorised than filed under the wrong direction")
    }

    @Test
    fun `a nested category tree is created parents first`() {
        val nested = HomeMoneyPlan(
            accounts = plan().accounts,
            categories = listOf(
                PlannedCategory("homemoney:3", "Diesel", "Car / Fuel / Diesel", CategoryType.EXPENSE, "homemoney:2", false),
                PlannedCategory("homemoney:2", "Fuel", "Car / Fuel", CategoryType.EXPENSE, "homemoney:1", false),
                PlannedCategory("homemoney:1", "Car", "Car", CategoryType.EXPENSE, null, false)
            )
        )

        service.prepare(userId, nested)

        val byExternal = categoryRepo.findAllByUser(user()).associateBy { it.externalId }
        assertEquals("Car", byExternal["homemoney:2"]!!.parent!!.name)
        assertEquals("Fuel", byExternal["homemoney:3"]!!.parent!!.name)
        assertNull(byExternal["homemoney:1"]!!.parent)
    }

    private fun user(): User = userRepo.findById(userId).orElseThrow()

    /** Everything this user has in the ledger; the repository has no all-by-user finder. */
    private fun ledger() = transactionRepo.findAll().filter { it.user.id == userId }
}
