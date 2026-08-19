package com.personalmoney.core.domain.model

import com.personalmoney.core.domain.TransactionType
import kotlinx.datetime.LocalDate

/**
 * A statement file, as the rest of the app thinks about one.
 *
 * Every bank exports CSV, including the ones with no API a small service can reach, so
 * this covers far more ground than any single integration ever will.
 */

/**
 * Which column of the file means what.
 *
 * These are header names, not values. Detection is a guess from the header row and the
 * user gets to correct it, which is why the guess is part of the preview rather than
 * something the server keeps to itself.
 */
data class CsvColumnMapping(
    val date: String,
    val description: String,
    /** A single signed column, or [debit] and [credit] as a pair. Banks do both. */
    val amount: String? = null,
    val debit: String? = null,
    val credit: String? = null,
    val currency: String? = null,
    val dateFormat: String? = null,
)

/**
 * One line of the file, after parsing and before the user has agreed to it.
 *
 * [occurredAtRaw] is deliberately not a parsed instant. The confirm hands these rows back
 * to the server unchanged - the file was the client's own upload, so there is no more
 * authoritative copy to re-read - and parsing a timestamp only to print it again is a
 * chance to hand back something subtly different. [date] is the parsed part, because that
 * is the part a person reads.
 */
data class CsvRow(
    val externalId: String,
    val occurredAtRaw: String,
    val date: LocalDate,
    val description: String,
    /** Positive magnitude; the direction is in [type], as everywhere else in the app. */
    val amountMinor: Long,
    /** Null when the file has no currency column at all. */
    val currencyCode: String? = null,
    val type: TransactionType,
    val alreadyImported: Boolean = false,
    val suggestedCategoryId: Long? = null,
)

data class CsvPreview(
    /** Every header in the file, so the mapping editor can offer them. */
    val headers: List<String> = emptyList(),
    val mapping: CsvColumnMapping,
    val rows: List<CsvRow> = emptyList(),
    /** Lines that would not parse. Shown, so they are visible rather than missing. */
    val skipped: List<String> = emptyList(),
    /**
     * Currencies in the file that are not the target account's. A warning rather than a
     * conversion: importing a GBP file into a UAH account would misstate the balance, and
     * doing it silently would misstate it invisibly.
     */
    val currencyMismatch: List<String> = emptyList(),
) {
    /** Rows that would actually land; the rest are already in the ledger. */
    val fresh: List<CsvRow> get() = rows.filter { !it.alreadyImported }
}
