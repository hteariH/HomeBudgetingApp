package com.personalmoney.core.data

import com.personalmoney.core.domain.model.CsvColumnMapping
import com.personalmoney.core.domain.model.CsvPreview
import com.personalmoney.core.domain.model.CsvRow
import com.personalmoney.core.domain.model.ImportResult

/**
 * Importing a statement file.
 *
 * No flow on this one at all, unlike every other repository here. There is nothing to
 * observe: a file is read once, looked at once, and either imported or abandoned. Handing
 * back a flow would promise a stream of updates that nothing is ever going to send.
 *
 * Bound only in the web graph, for the same reason as [BankRepository]: an import writes
 * to the server's ledger, and the phone reads its own out of Room.
 */
interface CsvImportRepository {

    /**
     * Reads the file and reports what it found, writing nothing.
     *
     * [mapping] is null the first time, which asks the server to detect the columns; the
     * detected mapping comes back in [CsvPreview.mapping] and is passed here again once
     * the user corrects it.
     */
    suspend fun preview(
        accountId: Long,
        fileName: String,
        bytes: ByteArray,
        mapping: CsvColumnMapping? = null,
    ): CsvPreview

    /**
     * Imports [rows]. [categories] maps [CsvRow.externalId] to a category, or to null to
     * import that row uncategorised. [transfers] maps it instead to another account, for a
     * line that only moved money between the user's own accounts: those become a transfer
     * pair rather than an expense, which is the difference between a month that reads right
     * and one that reads as if the savings top-up had been spent.
     *
     * The rows go back up because the server has no copy of the file to re-read. It still
     * checks each one against the ledger, so a row imported by another tab in the meantime
     * is skipped rather than duplicated.
     */
    suspend fun confirm(
        accountId: Long,
        rows: List<CsvRow>,
        categories: Map<String, Long?>,
        transfers: Map<String, Long?> = emptyMap(),
    ): ImportResult
}
