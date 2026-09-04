package com.personalmoney.feature.banks

import app.cash.turbine.test
import com.personalmoney.core.data.CsvImportRepository
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.model.CsvColumnMapping
import com.personalmoney.core.domain.model.CsvPreview
import com.personalmoney.core.domain.model.CsvRow
import com.personalmoney.core.domain.model.ImportResult
import com.personalmoney.core.ui.PickedFile
import com.personalmoney.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * The two things a CSV import must not get wrong.
 *
 * Rows go back to the server on confirm, because the file was the client's own upload and
 * there is nothing more authoritative to re-read. That makes this client the only guard
 * against importing a duplicate, and the only thing standing between a timestamp and a
 * lossy round trip.
 */
class CsvImportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<CsvImportRepository>(relaxed = true)

    private val mapping = CsvColumnMapping(date = "Date", description = "Details", amount = "Amount")

    private fun row(externalId: String, alreadyImported: Boolean = false) = CsvRow(
        externalId = externalId,
        // A form the app never parses, precisely so it cannot reformat it.
        occurredAtRaw = "2026-08-01T09:30:00.123456Z",
        date = LocalDate(2026, 8, 1),
        description = "Shop $externalId",
        amountMinor = 1_250L,
        currencyCode = "USD",
        type = TransactionType.EXPENSE,
        alreadyImported = alreadyImported,
    )

    private fun preview(rows: List<CsvRow>) = CsvPreview(
        headers = listOf("Date", "Details", "Amount"),
        mapping = mapping,
        rows = rows,
    )

    private fun vm(rows: List<CsvRow>): CsvImportViewModel {
        coEvery { repository.preview(any(), any(), any(), any()) } returns preview(rows)
        coEvery { repository.confirm(any(), any(), any(), any()) } returns ImportResult(1, 0)
        return CsvImportViewModel(repository)
    }

    private fun file() = PickedFile("statement.csv", "Date,Details,Amount\n".encodeToByteArray())

    @Test
    fun `confirm sends the fresh rows with their timestamps untouched`() = runTest {
        val model = vm(listOf(row("a"), row("b", alreadyImported = true)))
        model.state.test {
            skipItems(1)
            model.setAccount(7L)
            model.setFile(file())
            model.read()
            var state = awaitItem()
            while (state.preview == null) state = awaitItem()

            model.confirm(onImported = {})
            while (state.result == null) state = awaitItem()

            val sent = slot<List<CsvRow>>()
            coVerify { repository.confirm(eq(7L), capture(sent), any(), any()) }
            // The duplicate never leaves: the server writes what it is handed, so sending
            // it would be asking for the row twice.
            assertEquals(listOf("a"), sent.captured.map { it.externalId })
            assertEquals("2026-08-01T09:30:00.123456Z", sent.captured.single().occurredAtRaw)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `correcting a column re-reads the same file with the new mapping`() = runTest {
        val model = vm(listOf(row("a")))
        model.state.test {
            skipItems(1)
            model.setAccount(7L)
            model.setFile(file())
            model.read()
            var state = awaitItem()
            while (state.preview == null) state = awaitItem()

            model.setMappingColumn(CsvMappingField.DATE, "Completed")

            // Every row is derived from the mapping, so a correction is not a display
            // change - the file has to be read again.
            val used = slot<CsvColumnMapping>()
            coVerify { repository.preview(eq(7L), eq("statement.csv"), any(), capture(used)) }
            assertEquals("Completed", used.captured.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a row marked as a transfer is sent as a counterpart account, not as a category`() = runTest {
        val model = vm(listOf(row("a")))
        model.state.test {
            skipItems(1)
            model.setAccount(7L)
            model.setFile(file())
            model.read()
            var state = awaitItem()
            while (state.preview == null) state = awaitItem()

            model.setRowChoice("a", ImportChoice.encodeAccount(9L))
            model.confirm(onImported = {})
            while (state.result == null) state = awaitItem()

            val sentCategories = slot<Map<String, Long?>>()
            val sentTransfers = slot<Map<String, Long?>>()
            coVerify {
                repository.confirm(eq(7L), any(), capture(sentCategories), capture(sentTransfers))
            }
            // The row still has to appear in the categories map: that map is the selection,
            // and a row missing from it would not be imported at all.
            assertEquals(mapOf("a" to null), sentCategories.captured)
            assertEquals(mapOf("a" to 9L), sentTransfers.captured)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a spent file is cleared so the same statement is not imported twice`() = runTest {
        val model = vm(listOf(row("a")))
        model.state.test {
            skipItems(1)
            model.setAccount(7L)
            model.setFile(file())
            model.read()
            var state = awaitItem()
            while (state.preview == null) state = awaitItem()

            model.confirm(onImported = {})
            while (state.result == null) state = awaitItem()

            assertNull(state.fileName)
            assertNull(state.preview)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
