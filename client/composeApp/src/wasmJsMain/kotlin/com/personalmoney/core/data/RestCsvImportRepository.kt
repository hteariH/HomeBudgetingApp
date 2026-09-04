package com.personalmoney.core.data

import com.personalmoney.core.domain.model.CsvColumnMapping
import com.personalmoney.core.domain.model.CsvPreview
import com.personalmoney.core.domain.model.CsvRow
import com.personalmoney.core.domain.model.ImportResult
import com.personalmoney.core.network.CsvConfirmRequest
import com.personalmoney.core.network.RestApi
import com.personalmoney.core.network.RestCsvMapping
import com.personalmoney.core.network.RestCsvPreview
import com.personalmoney.core.network.RestCsvRow
import kotlinx.datetime.LocalDate

/**
 * The shared CSV contract, backed by REST. See [RestAccountRepository].
 *
 * No cache and no [LazyRefresh] here, unlike its neighbours: there is no list to keep warm,
 * only two calls that each answer once.
 */
class RestCsvImportRepository(private val api: RestApi) : CsvImportRepository {

    override suspend fun preview(
        accountId: Long,
        fileName: String,
        bytes: ByteArray,
        mapping: CsvColumnMapping?,
    ): CsvPreview = api
        .previewCsv(accountId, fileName, bytes, mapping?.toDto())
        .toPreview()

    override suspend fun confirm(
        accountId: Long,
        rows: List<CsvRow>,
        categories: Map<String, Long?>,
        transfers: Map<String, Long?>,
    ): ImportResult {
        val result = api.confirmCsv(
            CsvConfirmRequest(
                accountId = accountId,
                rows = rows.map { it.toDto() },
                categories = categories,
                transfers = transfers,
            )
        )
        return ImportResult(imported = result.imported, skipped = result.skipped)
    }
}

private fun CsvColumnMapping.toDto() = RestCsvMapping(
    date = date,
    description = description,
    amount = amount,
    debit = debit,
    credit = credit,
    currency = currency,
    dateFormat = dateFormat,
)

private fun RestCsvMapping.toMapping() = CsvColumnMapping(
    date = date,
    description = description,
    amount = amount,
    debit = debit,
    credit = credit,
    currency = currency,
    dateFormat = dateFormat,
)

private fun RestCsvPreview.toPreview() = CsvPreview(
    headers = headers,
    mapping = mapping.toMapping(),
    rows = rows.map { it.toRow() },
    skipped = skipped,
    currencyMismatch = currencyMismatch,
)

private fun RestCsvRow.toRow() = CsvRow(
    externalId = externalId,
    occurredAtRaw = occurredAt,
    date = LocalDate.parse(date),
    description = description,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    type = type,
    alreadyImported = alreadyImported,
    suggestedCategoryId = suggestedCategoryId,
)

/**
 * Back into the shape the server sent. [CsvRow.occurredAtRaw] goes back untouched, which
 * is the whole reason it was kept as text.
 */
private fun CsvRow.toDto() = RestCsvRow(
    externalId = externalId,
    occurredAt = occurredAtRaw,
    date = date.toString(),
    description = description,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    type = type,
    alreadyImported = alreadyImported,
    suggestedCategoryId = suggestedCategoryId,
)
