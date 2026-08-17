package com.homebudget.app.bank

import com.homebudget.app.domain.TransactionType
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Which CSV columns mean what.
 *
 * Exists because every bank exports a different shape and no amount of guessing covers
 * them all - so the guess is a starting point the user can correct, not a hard rule.
 */
data class ColumnMapping(
    val date: String,
    val description: String,
    /** Single signed column: negative is money out. */
    val amount: String? = null,
    /** Or a separate pair, which is what ledger-style exports use. */
    val debit: String? = null,
    val credit: String? = null,
    val currency: String? = null,
    /** The bank's own reference, when the export has one. */
    val externalId: String? = null,
    val dateFormat: String? = null
) {
    init {
        require(amount != null || (debit != null || credit != null)) {
            "Mapping needs either an amount column or a debit/credit pair"
        }
    }
}

data class CsvRow(
    val externalId: String,
    val occurredAt: java.time.Instant,
    val description: String,
    val amountMinor: Long,
    val currencyCode: String?,
    val type: TransactionType
)

data class CsvPreview(
    val headers: List<String>,
    val mapping: ColumnMapping,
    val rows: List<CsvRow>,
    val skipped: List<String>
)

class CsvParseException(message: String) : RuntimeException(message)

/**
 * Parses a bank statement export.
 *
 * Deliberately format-agnostic rather than one parser per bank: a CSV export is the one
 * thing every bank offers, including the ones with no usable API at all, so a single
 * configurable reader covers far more ground than any single integration.
 *
 * Rows are given a synthetic [CsvRow.externalId] derived from their content, because
 * most exports carry no stable reference. That is what makes re-importing an overlapping
 * date range - which people do constantly - skip what is already there instead of
 * duplicating it.
 */
object CsvStatementParser {

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd/MM/yyyy",
        "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy",
        "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm", "dd.MM.yyyy"
    )

    private val DATE_HINTS = listOf("completed date", "started date", "date", "дата", "time", "posted")
    private val DESC_HINTS = listOf("description", "narrative", "details", "reference", "опис", "описание", "payee")
    private val AMOUNT_HINTS = listOf("amount", "sum", "сума", "сумма", "value")
    private val DEBIT_HINTS = listOf("debit", "withdrawal", "out", "расход")
    private val CREDIT_HINTS = listOf("credit", "deposit", "in", "приход")
    private val CURRENCY_HINTS = listOf("currency", "ccy", "валюта")
    private val ID_HINTS = listOf("transaction id", "id", "reference id")

    /** Best-effort mapping from the header row, offered to the user for correction. */
    fun detectMapping(headers: List<String>): ColumnMapping {
        fun find(hints: List<String>): String? = hints.firstNotNullOfOrNull { hint ->
            headers.firstOrNull { it.trim().equals(hint, ignoreCase = true) }
                ?: headers.firstOrNull { it.trim().contains(hint, ignoreCase = true) }
        }

        val date = find(DATE_HINTS) ?: throw CsvParseException(
            "No date column found in: ${headers.joinToString(", ")}"
        )
        val description = find(DESC_HINTS) ?: headers.firstOrNull { it != date }
            ?: throw CsvParseException("No description column found")
        val amount = find(AMOUNT_HINTS)
        val debit = find(DEBIT_HINTS)
        val credit = find(CREDIT_HINTS)

        if (amount == null && debit == null && credit == null) {
            throw CsvParseException("No amount column found in: ${headers.joinToString(", ")}")
        }

        return ColumnMapping(
            date = date,
            description = description,
            amount = amount,
            debit = debit.takeIf { amount == null },
            credit = credit.takeIf { amount == null },
            currency = find(CURRENCY_HINTS),
            externalId = find(ID_HINTS)
        )
    }

    fun parse(content: String, mapping: ColumnMapping? = null): CsvPreview {
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) throw CsvParseException("The file is empty")

        val delimiter = detectDelimiter(lines.first())
        val headers = splitLine(lines.first(), delimiter).map { it.trim() }
        val resolved = mapping ?: detectMapping(headers)

        fun indexOf(column: String?): Int? = column?.let { name ->
            headers.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
                ?: throw CsvParseException("Column '$name' is not in the file")
        }

        val dateIdx = indexOf(resolved.date)!!
        val descIdx = indexOf(resolved.description)!!
        val amountIdx = indexOf(resolved.amount)
        val debitIdx = indexOf(resolved.debit)
        val creditIdx = indexOf(resolved.credit)
        val currencyIdx = indexOf(resolved.currency)
        val idIdx = indexOf(resolved.externalId)

        val rows = mutableListOf<CsvRow>()
        val skipped = mutableListOf<String>()

        for (line in lines.drop(1)) {
            val cells = splitLine(line, delimiter)
            fun cell(i: Int?): String? = i?.let { cells.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }

            val rawDate = cell(dateIdx)
            if (rawDate == null) { skipped += "no date: $line"; continue }

            val occurredAt = try {
                parseDate(rawDate, resolved.dateFormat)
            } catch (e: Exception) {
                skipped += "unparseable date '$rawDate'"; continue
            }

            val decimal = try {
                when {
                    amountIdx != null -> cell(amountIdx)?.let { parseAmount(it) }
                    else -> {
                        val debit = cell(debitIdx)?.let { parseAmount(it) } ?: BigDecimal.ZERO
                        val credit = cell(creditIdx)?.let { parseAmount(it) } ?: BigDecimal.ZERO
                        // Ledger exports put the magnitude in one column or the other.
                        credit.subtract(debit.abs())
                    }
                }
            } catch (e: Exception) {
                skipped += "unparseable amount in: $line"; continue
            }

            if (decimal == null || decimal.signum() == 0) { skipped += "zero or missing amount"; continue }

            val description = cell(descIdx) ?: "—"
            val type = if (decimal.signum() < 0) TransactionType.EXPENSE else TransactionType.INCOME

            rows += CsvRow(
                // A content hash, so importing an overlapping range twice is a no-op
                // rather than a duplicate. Most exports have no stable reference.
                externalId = cell(idIdx) ?: syntheticId(occurredAt, decimal, description),
                occurredAt = occurredAt,
                description = description,
                amountMinor = decimal.abs().movePointRight(2).toLong(),
                currencyCode = cell(currencyIdx)?.uppercase(),
                type = type
            )
        }

        return CsvPreview(headers, resolved, rows, skipped)
    }

    /** Semicolon is common wherever the comma is a decimal separator. */
    private fun detectDelimiter(headerLine: String): Char =
        listOf(',', ';', '\t').maxByOrNull { headerLine.count { c -> c == it } } ?: ','

    /** Minimal RFC 4180: quoted fields may contain the delimiter and doubled quotes. */
    internal fun splitLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> { cells += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }

    internal fun parseAmount(raw: String): BigDecimal {
        var s = raw.trim().replace(" ", "").replace(" ", "")
        // Amounts arrive as "1 234,56", "1,234.56" or "(45.00)" depending on the locale.
        val negative = s.startsWith("(") && s.endsWith(")")
        if (negative) s = s.substring(1, s.length - 1)
        s = s.filter { it.isDigit() || it == ',' || it == '.' || it == '-' || it == '+' }

        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                // Whichever comes last is the decimal separator.
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 ->
                // A lone comma is decimal unless it is grouping three digits.
                if (s.length - lastComma - 1 == 3) s.replace(",", "") else s.replace(',', '.')
            else -> s
        }
        val value = BigDecimal(s)
        return if (negative) value.negate() else value
    }

    private fun parseDate(raw: String, explicitFormat: String?): java.time.Instant {
        val cleaned = raw.trim()
        val patterns = explicitFormat?.let { listOf(it) } ?: DATE_PATTERNS
        for (pattern in patterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern)
                return if (pattern.contains("H")) {
                    LocalDateTime.parse(cleaned, formatter).toInstant(ZoneOffset.UTC)
                } else {
                    LocalDate.parse(cleaned, formatter).atStartOfDay(ZoneOffset.UTC).toInstant()
                }
            } catch (_: Exception) {
                // Try the next pattern.
            }
        }
        throw CsvParseException("Unrecognised date: $raw")
    }

    private fun syntheticId(occurredAt: java.time.Instant, amount: BigDecimal, description: String): String {
        val material = "$occurredAt|${amount.stripTrailingZeros().toPlainString()}|${description.lowercase().trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
        return "csv:" + digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
