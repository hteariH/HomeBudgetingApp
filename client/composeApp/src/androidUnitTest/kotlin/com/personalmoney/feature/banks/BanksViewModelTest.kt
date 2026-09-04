package com.personalmoney.feature.banks

import app.cash.turbine.test
import com.personalmoney.core.data.AccountRepository
import com.personalmoney.core.data.BankRepository
import com.personalmoney.core.data.CategoryRepository
import com.personalmoney.core.data.TransactionRepository
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.BankConnection
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.ImportResult
import com.personalmoney.core.domain.model.PendingImport
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The import selection is the part worth pinning down.
 *
 * `categories` is not only how a row is categorised - it is *which* rows get imported, and
 * the server writes exactly the external ids it finds in that map. Getting it wrong would
 * put duplicates in somebody's ledger, and nothing in the type system would notice.
 */
class BanksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val banks = mockk<BankRepository>(relaxed = true)
    private val accounts = mockk<AccountRepository>(relaxed = true)
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val categories = mockk<CategoryRepository>(relaxed = true)

    private val checking = Account(id = 7L, name = "Checking", currencyCode = "USD")
    private val savings = Account(id = 9L, name = "Savings", currencyCode = "USD")

    private val groceries =
        Category(id = 3L, name = "Groceries", type = CategoryType.EXPENSE, icon = "cart", color = 0L)

    private val connection = BankConnection(
        id = 1L,
        provider = "MONOBANK",
        providerDisplayName = "Monobank",
        linkedAccountIds = listOf(7L),
    )

    private fun row(externalId: String, alreadyImported: Boolean = false, suggested: Long? = null) =
        PendingImport(
            externalId = externalId,
            date = LocalDate(2026, 8, 1),
            description = "Shop $externalId",
            amountMinor = 1_250L,
            type = TransactionType.EXPENSE,
            alreadyImported = alreadyImported,
            suggestedCategoryId = suggested,
        )

    private fun vm(rows: List<PendingImport> = emptyList()): BanksViewModel {
        every { banks.observeConnections() } returns flowOf(listOf(connection))
        coEvery { banks.providers() } returns emptyList()
        coEvery { banks.previewImport(any(), any(), any()) } returns rows
        coEvery { banks.confirmImport(any(), any(), any(), any(), any()) } returns ImportResult(1, 0)
        every { accounts.observeActive() } returns flowOf(listOf(checking, savings))
        every { categories.observeAll() } returns flowOf(listOf(groceries))
        return BanksViewModel(banks, accounts, transactions, categories)
    }

    @Test
    fun `only accounts a connection feeds can be imported into`() = runTest {
        vm().uiState.test {
            var state = awaitItem()
            while (state.accounts.isEmpty()) state = awaitItem()

            assertEquals(listOf(checking), state.linkedAccounts)
            // Savings is free, so it is what the link picker offers instead.
            assertEquals(listOf(savings), state.linkCandidates("USD"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a linked account in another currency is not offered`() = runTest {
        vm().uiState.test {
            var state = awaitItem()
            while (state.accounts.isEmpty()) state = awaitItem()

            assertTrue(state.linkCandidates("EUR").isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `preview pre-fills the server's category guess and leaves duplicates alone`() = runTest {
        val model = vm(listOf(row("a", suggested = 3L), row("b", alreadyImported = true, suggested = 3L)))
        model.uiState.test {
            var state = awaitItem()
            while (state.accounts.isEmpty()) state = awaitItem()

            model.setImportAccount(7L)
            model.preview()
            while (state.import.rows == null) state = awaitItem()

            assertEquals(mapOf("a" to 3L), state.import.categories)
            assertEquals(1, state.import.freshCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirm sends the fresh rows only, with the categories the user chose`() = runTest {
        val model = vm(listOf(row("a", suggested = 3L), row("b", alreadyImported = true)))
        model.uiState.test {
            var state = awaitItem()
            while (state.accounts.isEmpty()) state = awaitItem()

            model.setImportAccount(7L)
            model.preview()
            while (state.import.rows == null) state = awaitItem()

            model.setRowChoice("a", null)
            model.confirmImport()
            while (state.import.result == null) state = awaitItem()

            // "b" is absent rather than present-and-ignored: absence is what keeps a row
            // that is already in the ledger from being written a second time.
            coVerify { banks.confirmImport(7L, any(), any(), mapOf("a" to null), emptyMap()) }
            assertEquals(ImportResult(1, 0), state.import.result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a line marked as a transfer travels as a counterpart account, not as a category`() = runTest {
        val model = vm(listOf(row("a", suggested = 3L)))
        model.uiState.test {
            var state = awaitItem()
            while (state.accounts.isEmpty()) state = awaitItem()

            model.setImportAccount(7L)
            model.preview()
            while (state.import.rows == null) state = awaitItem()

            model.setRowChoice("a", ImportChoice.encodeAccount(savings.id))
            model.confirmImport()
            while (state.import.result == null) state = awaitItem()

            // The suggested category has to go with it: a line cannot be both a move
            // between the user's own accounts and money spent on groceries.
            coVerify {
                banks.confirmImport(7L, any(), any(), mapOf("a" to null), mapOf("a" to 9L))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the account throws the preview away`() = runTest {
        val model = vm(listOf(row("a")))
        model.uiState.test {
            var state = awaitItem()
            while (state.accounts.isEmpty()) state = awaitItem()

            model.setImportAccount(7L)
            model.preview()
            while (state.import.rows == null) state = awaitItem()

            // Those rows belonged to the old account; keeping them would offer to import
            // one account's statement into another.
            model.setImportAccount(9L)
            while (state.import.rows != null) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
