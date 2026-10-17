package com.personalmoney.core.data.repository

import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.Transaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

/** Who wrote a row is the server's to say; the phone only carries it. */
class RoomTransactionRepositoryTest {

    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val delegate = mockk<TransactionRepository>(relaxed = true)
    private val repo = RoomTransactionRepository(delegate)

    private val stored = TransactionEntity(
        id = 4, type = TransactionType.EXPENSE, accountId = 1, categoryId = null,
        amountMinor = -500, currencyCode = "USD", accountAmountMinor = -500,
        occurredAt = at, createdAt = at, uuid = "tx-4", createdByUserId = 7L,
    )

    private fun edited(author: Long?) = Transaction(
        id = 4, uuid = "tx-4", type = TransactionType.EXPENSE, accountId = 1, categoryId = null,
        amountMinor = -900, currencyCode = "USD", accountAmountMinor = -900,
        occurredAt = at, createdByUserId = author,
    )

    @Test
    fun `an edit keeps the stored author`() = runTest {
        coEvery { delegate.findById(4) } returns stored

        // Whatever the model says, the stored value wins: an edit must not reassign a row.
        repo.update(edited(author = null))
        repo.update(edited(author = 99L))

        coVerify(exactly = 2) { delegate.update(match { it.createdByUserId == 7L && it.amountMinor == -900L }) }
    }

    @Test
    fun `the author reaches the model`() = runTest {
        coEvery { delegate.findById(4) } returns stored

        assertEquals(7L, repo.findById(4)?.createdByUserId)
    }

    @Test
    fun `an edit keeps the receipt the entry was scanned from`() = runTest {
        coEvery { delegate.findById(4) } returns stored.copy(receiptUuid = "r-1")

        repo.update(edited(author = null))

        coVerify { delegate.update(match { it.receiptUuid == "r-1" && it.amountMinor == -900L }) }
    }

    @Test
    fun `the receipt reaches the model`() = runTest {
        coEvery { delegate.findById(4) } returns stored.copy(receiptUuid = "r-1")

        assertEquals("r-1", repo.findById(4)?.receiptUuid)
    }

    @Test
    fun `an edit keeps the stored source`() = runTest {
        coEvery { delegate.findById(4) } returns stored.copy(source = "MCP")

        repo.update(edited(author = null))

        coVerify { delegate.update(match { it.source == "MCP" && it.amountMinor == -900L }) }
    }

    @Test
    fun `the source reaches the model`() = runTest {
        coEvery { delegate.findById(4) } returns stored.copy(source = "MCP")

        assertEquals("MCP", repo.findById(4)?.source)
    }
}
