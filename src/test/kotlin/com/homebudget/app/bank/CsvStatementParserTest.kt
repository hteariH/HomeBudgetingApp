package com.homebudget.app.bank

import com.homebudget.app.domain.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class CsvStatementParserTest {

    /** Shaped like a Revolut export: signed amount, separate started/completed dates. */
    private val revolutish = """
        Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
        CARD_PAYMENT,Current,2026-08-01 09:12:00,2026-08-01 09:12:30,Tesco,-24.50,0.00,GBP,COMPLETED,975.50
        TOPUP,Current,2026-08-02 10:00:00,2026-08-02 10:00:10,Salary,1500.00,0.00,GBP,COMPLETED,2475.50
    """.trimIndent()

    @Test
    fun `detects columns from a Revolut-shaped header`() {
        val preview = CsvStatementParser.parse(revolutish)
        assertEquals("Completed Date", preview.mapping.date)
        assertEquals("Description", preview.mapping.description)
        assertEquals("Amount", preview.mapping.amount)
        assertEquals("Currency", preview.mapping.currency)
        assertEquals(2, preview.rows.size)
    }

    @Test
    fun `a negative amount is an expense and a positive one is income`() {
        val rows = CsvStatementParser.parse(revolutish).rows
        assertEquals(TransactionType.EXPENSE, rows[0].type)
        assertEquals(2450L, rows[0].amountMinor)
        assertEquals(TransactionType.INCOME, rows[1].type)
        assertEquals(150000L, rows[1].amountMinor)
    }

    @Test
    fun `re-importing the same rows yields the same ids`() {
        // The property the whole dedup story rests on: people re-export overlapping
        // ranges constantly, and without stable ids every import would duplicate.
        val first = CsvStatementParser.parse(revolutish).rows.map { it.externalId }
        val second = CsvStatementParser.parse(revolutish).rows.map { it.externalId }
        assertEquals(first, second)
        assertNotEquals(first[0], first[1], "different rows must not collide")
    }

    @Test
    fun `handles a semicolon file with comma decimals`() {
        // Common wherever the comma is the decimal separator, which breaks naive splitting.
        val csv = """
            Дата;Описание;Сумма;Валюта
            01.08.2026;Продукты;-1 234,56;UAH
            02.08.2026;Зарплата;25000,00;UAH
        """.trimIndent()
        val preview = CsvStatementParser.parse(csv)
        assertEquals(2, preview.rows.size)
        assertEquals(123456L, preview.rows[0].amountMinor)
        assertEquals(TransactionType.EXPENSE, preview.rows[0].type)
        assertEquals(2500000L, preview.rows[1].amountMinor)
        assertEquals("UAH", preview.rows[0].currencyCode)
    }

    @Test
    fun `handles separate debit and credit columns`() {
        val csv = """
            Date,Narrative,Debit,Credit
            2026-08-01,Rent,800.00,
            2026-08-05,Refund,,45.20
        """.trimIndent()
        val mapping = ColumnMapping(date = "Date", description = "Narrative", debit = "Debit", credit = "Credit")
        val rows = CsvStatementParser.parse(csv, mapping).rows
        assertEquals(TransactionType.EXPENSE, rows[0].type)
        assertEquals(80000L, rows[0].amountMinor)
        assertEquals(TransactionType.INCOME, rows[1].type)
        assertEquals(4520L, rows[1].amountMinor)
    }

    @Test
    fun `keeps a quoted delimiter inside a field`() {
        val csv = """
            Date,Description,Amount
            2026-08-01,"Cafe, downtown",-12.00
        """.trimIndent()
        val rows = CsvStatementParser.parse(csv).rows
        assertEquals("Cafe, downtown", rows.single().description)
    }

    @Test
    fun `reports unusable rows instead of dropping them silently`() {
        val csv = """
            Date,Description,Amount
            2026-08-01,Good,-10.00
            not-a-date,Bad,-10.00
            2026-08-03,Zero,0.00
        """.trimIndent()
        val preview = CsvStatementParser.parse(csv)
        assertEquals(1, preview.rows.size)
        assertEquals(2, preview.skipped.size, "skipped rows must be surfaced, not swallowed")
    }

    @Test
    fun `parses the amount formats banks actually emit`() {
        assertEquals(BigDecimal("-24.50"), CsvStatementParser.parseAmount("-24.50"))
        assertEquals(BigDecimal("1234.56"), CsvStatementParser.parseAmount("1,234.56"))
        assertEquals(BigDecimal("1234.56"), CsvStatementParser.parseAmount("1 234,56"))
        assertEquals(BigDecimal("-45.00"), CsvStatementParser.parseAmount("(45.00)"))
        // A comma grouping exactly three digits is thousands, not decimals.
        assertEquals(BigDecimal("1500"), CsvStatementParser.parseAmount("1,500"))
    }

    @Test
    fun `refuses a file with no recognisable amount column`() {
        val csv = "Date,Description\n2026-08-01,Something"
        assertThrows<CsvParseException> { CsvStatementParser.parse(csv) }
    }

    @Test
    fun `refuses an empty file`() {
        assertThrows<CsvParseException> { CsvStatementParser.parse("   ") }
    }

    @Test
    fun `an explicit mapping overrides detection`() {
        // Revolut has two date columns; the user may want the started one.
        val mapping = ColumnMapping(
            date = "Started Date", description = "Description", amount = "Amount", currency = "Currency"
        )
        val preview = CsvStatementParser.parse(revolutish, mapping)
        assertEquals("Started Date", preview.mapping.date)
        assertTrue(preview.rows.isNotEmpty())
    }
}
