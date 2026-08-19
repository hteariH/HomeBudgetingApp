package com.personalmoney.core.network

import com.personalmoney.core.domain.TransactionType
import kotlinx.serialization.Serializable

/**
 * The CSV import contract, captured from `CsvImportController` in the server repository.
 *
 * Unlike a bank import, confirm sends the rows back rather than naming a date range. That
 * is not laziness on either side: the file is the client's own upload, so there is no more
 * authoritative copy for the server to re-read.
 *
 * Which is why [RestCsvRow.occurredAt] is a `String` here and stays one all the way to the
 * confirm. The app never needs to *do* anything with the instant - it only has to hand
 * back what it was given, and a parse-and-reformat round trip is a chance to hand back
 * something subtly different.
 */

/**
 * Which column means what.
 *
 * All of these are header names from the file, not values. The parser guesses them and
 * sends its guess back so the user can correct it - a file with two date columns (Revolut
 * ships "Started" and "Completed") has no single right answer.
 */
@Serializable
data class RestCsvMapping(
    val date: String,
    val description: String,
    /** One signed column, or [debit]/[credit] as a pair. Banks do it both ways. */
    val amount: String? = null,
    val debit: String? = null,
    val credit: String? = null,
    val currency: String? = null,
    val externalId: String? = null,
    val dateFormat: String? = null,
)

@Serializable
data class RestCsvRow(
    val externalId: String,
    /** Echoed back verbatim on confirm; see the file comment. */
    val occurredAt: String,
    val date: String,
    val description: String,
    /** Positive magnitude; the direction is in [type]. */
    val amountMinor: Long,
    /** Null when the file has no currency column at all. */
    val currencyCode: String? = null,
    val type: TransactionType,
    val alreadyImported: Boolean = false,
    val suggestedCategoryId: Long? = null,
)

@Serializable
data class RestCsvPreview(
    val headers: List<String> = emptyList(),
    val mapping: RestCsvMapping,
    val rows: List<RestCsvRow> = emptyList(),
    /** Lines that would not parse, so they are visible rather than silently missing. */
    val skipped: List<String> = emptyList(),
    /** Currencies in the file that are not the target account's. */
    val currencyMismatch: List<String> = emptyList(),
)

@Serializable
data class CsvConfirmRequest(
    val accountId: Long,
    val rows: List<RestCsvRow> = emptyList(),
    /** External id to category id, or null to import that row uncategorised. */
    val categories: Map<String, Long?> = emptyMap(),
)
