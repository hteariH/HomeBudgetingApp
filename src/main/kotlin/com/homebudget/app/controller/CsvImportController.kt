package com.homebudget.app.controller

import com.homebudget.app.bank.BankConnectionService
import com.homebudget.app.bank.ColumnMapping
import com.homebudget.app.bank.CsvParseException
import com.homebudget.app.bank.CsvStatementParser
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.repository.TransactionRepository
import com.homebudget.app.service.AccountService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class CsvMappingDto(
    val date: String,
    val description: String,
    val amount: String? = null,
    val debit: String? = null,
    val credit: String? = null,
    val currency: String? = null,
    val externalId: String? = null,
    val dateFormat: String? = null
) {
    fun toMapping() = ColumnMapping(date, description, amount, debit, credit, currency, externalId, dateFormat)
}

data class CsvRowDto(
    val externalId: String,
    val occurredAt: Instant,
    val date: LocalDate,
    val description: String,
    val amountMinor: Long,
    val currencyCode: String?,
    val type: TransactionType,
    val alreadyImported: Boolean,
    val suggestedCategoryId: Long?
)

data class CsvPreviewResponse(
    val headers: List<String>,
    /** What was detected; send it back adjusted if it guessed wrong. */
    val mapping: CsvMappingDto,
    val rows: List<CsvRowDto>,
    /** Lines that could not be read, so they are visible rather than silently missing. */
    val skipped: List<String>,
    /** Currencies in the file that differ from the target account's. */
    val currencyMismatch: List<String>
)

data class CsvConfirmRequest(
    val accountId: Long,
    val rows: List<CsvRowDto> = emptyList(),
    val categories: Map<String, Long?> = emptyMap()
)

/**
 * Import from a statement file.
 *
 * Every bank exports CSV, including the ones with no API a small service can reach -
 * Revolut personal accounts among them, where the only programmatic route is a licensed
 * Open Banking aggregator. One configurable reader therefore covers far more ground
 * than any single integration.
 *
 * Preview and confirm are separate for the same reason as the bank imports: the user
 * assigns categories in between. Unlike a bank import, confirm takes the rows back from
 * the client rather than re-reading the file - the file is the client's own upload, so
 * there is no more authoritative copy to re-fetch.
 */
@RestController
@RequestMapping("/api/import/csv")
class CsvImportController(
    private val importService: BankConnectionService,
    private val accountService: AccountService,
    private val transactionRepo: TransactionRepository
) {

    @PostMapping("/preview", consumes = ["multipart/form-data"])
    fun preview(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("accountId") accountId: Long,
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) amount: String?,
        @RequestParam(required = false) debit: String?,
        @RequestParam(required = false) credit: String?,
        @RequestParam(required = false) currency: String?,
        @RequestParam(required = false) dateFormat: String?
    ): CsvPreviewResponse {
        val account = accountService.findById(accountId)

        // A mapping is only honoured when the caller supplied the mandatory parts;
        // otherwise detection runs and its guess comes back for correction.
        val mapping = if (date != null && description != null) {
            ColumnMapping(date, description, amount, debit, credit, currency, null, dateFormat)
        } else null

        val parsed = CsvStatementParser.parse(file.bytes.toString(Charsets.UTF_8), mapping)

        val rows = parsed.rows.map { row ->
            CsvRowDto(
                externalId = row.externalId,
                occurredAt = row.occurredAt,
                date = row.occurredAt.atZone(ZoneOffset.UTC).toLocalDate(),
                description = row.description,
                amountMinor = row.amountMinor,
                currencyCode = row.currencyCode,
                type = row.type,
                alreadyImported = transactionRepo.existsByAccountAndExternalId(account, row.externalId),
                suggestedCategoryId = null
            )
        }

        // Importing a GBP file into a UAH account would silently misstate the balance,
        // so the mismatch is reported rather than converted behind the user's back.
        val mismatch = rows.mapNotNull { it.currencyCode }
            .filter { it != account.currency.code }
            .distinct()

        return CsvPreviewResponse(
            headers = parsed.headers,
            mapping = CsvMappingDto(
                parsed.mapping.date, parsed.mapping.description, parsed.mapping.amount,
                parsed.mapping.debit, parsed.mapping.credit, parsed.mapping.currency,
                parsed.mapping.externalId, parsed.mapping.dateFormat
            ),
            rows = rows,
            skipped = parsed.skipped,
            currencyMismatch = mismatch
        )
    }

    @PostMapping("/confirm")
    fun confirm(@RequestBody req: CsvConfirmRequest): ImportResultResponse {
        val result = importService.importRows(
            accountId = req.accountId,
            rows = req.rows.map {
                BankConnectionService.ImportableRow(
                    externalId = it.externalId,
                    occurredAt = it.occurredAt,
                    description = it.description,
                    amountMinor = it.amountMinor,
                    type = it.type
                )
            },
            categoryByExternalId = req.categories
        )
        return ImportResultResponse(result.imported, result.skipped)
    }

    /** A malformed file is the user's to fix, so it must not surface as a 500. */
    @ExceptionHandler(CsvParseException::class)
    fun handleParseError(ex: CsvParseException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ErrorResponse(status = 422, error = "Cannot read the file", message = ex.message)
        )
}
