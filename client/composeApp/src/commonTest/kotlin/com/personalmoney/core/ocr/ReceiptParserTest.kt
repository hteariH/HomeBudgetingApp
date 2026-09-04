package com.personalmoney.core.ocr

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptParserTest {

    private val today = LocalDate(2026, 9, 3)

    // -- total ---------------------------------------------------------------------------

    @Test fun `takes the amount a total keyword vouches for`() {
        val receipt = ReceiptParser.parse(
            listOf(
                "ATB MARKET",
                "Milk 2.5%      1 x 42.90",
                "Bread          2 x 24.50",
                "TOTAL          91.90",
            ),
            today,
        )
        assertEquals("91.90", receipt.total)
    }

    /**
     * The case the largest-amount rule alone gets wrong: cash tendered is bigger than the
     * total. The keyword is what saves it - and it survives OCR because `СУМА` is four
     * Cyrillic letters with Latin lookalikes, which is exactly what a Latin-only model emits.
     */
    @Test fun `cash tendered does not beat the vouched total`() {
        val receipt = ReceiptParser.parse(
            listOf(
                "CYMA          249.90",
                "ГOTIBKA       500.00",
                "PEШTA         250.10",
            ),
            today,
        )
        assertEquals("249.90", receipt.total)
    }

    /** The same keyword matches whether the engine read Cyrillic or its Latin lookalikes. */
    @Test fun `matches the keyword in real Cyrillic too`() {
        val receipt = ReceiptParser.parse(listOf("СУМА 249.90", "ГОТІВКА 500.00"), today)
        assertEquals("249.90", receipt.total)
    }

    /**
     * The words no homoglyph fold can reach - Д, Л, Г and И have no Latin twin - so these
     * only started matching once the engine could read Cyrillic.
     */
    @Test fun `matches Cyrillic-only total words`() {
        assertEquals(
            "91.90",
            ReceiptParser.parse(listOf("ВСЬОГО 91.90", "ГОТІВКА 100.00"), today).total,
        )
        assertEquals(
            "91.90",
            ReceiptParser.parse(listOf("РАЗОМ 91.90", "ГОТІВКА 100.00"), today).total,
        )
        assertEquals(
            "91.90",
            ReceiptParser.parse(listOf("ИТОГО 91.90", "НАЛИЧНЫЕ 100.00"), today).total,
        )
    }

    @Test fun `a total keyword survives the space inside it`() {
        // "ДО СПЛАТИ" is two words on the receipt and one keyword here.
        val receipt = ReceiptParser.parse(listOf("ДО СПЛАТИ 91.90", "ГОТІВКА 100.00"), today)
        assertEquals("91.90", receipt.total)
    }

    @Test fun `a keyword vouches for the line under it`() {
        // Two-column receipts reach us as one column per block: the word, then the number.
        val receipt = ReceiptParser.parse(
            listOf("Bread 24.50", "СУМА", "91.90", "ПДВ 15.32"),
            today,
        )
        assertEquals("91.90", receipt.total)
    }

    @Test fun `falls back to the largest amount when no keyword survives`() {
        val receipt = ReceiptParser.parse(
            listOf("Xлiб 24.50", "Moлoкo 42.90", "Дo cплaти 67.40"),
            today,
        )
        assertEquals("67.40", receipt.total)
    }

    @Test fun `reads a comma decimal separator`() {
        assertEquals("249.90", ReceiptParser.parse(listOf("СУМА 249,90"), today).total)
    }

    @Test fun `reads spaced thousands`() {
        assertEquals("1249.90", ReceiptParser.parse(listOf("СУМА 1 249.90"), today).total)
    }

    @Test fun `a date is not an amount`() {
        // 03.09 would be money-shaped if the year did not follow it.
        val receipt = ReceiptParser.parse(listOf("03.09.2026 14:22", "СУМА 42.00"), today)
        assertEquals("42.00", receipt.total)
    }

    @Test fun `no amount at all`() {
        assertNull(ReceiptParser.parse(listOf("ATB MARKET", "Dyakuyemo"), today).total)
    }

    // -- date ----------------------------------------------------------------------------

    @Test fun `reads a day-first date`() {
        assertEquals(
            LocalDate(2026, 8, 29),
            ReceiptParser.parse(listOf("29.08.2026 19:03", "СУМА 42.00"), today).date,
        )
    }

    @Test fun `reads an iso date`() {
        assertEquals(
            LocalDate(2026, 8, 29),
            ReceiptParser.parse(listOf("2026-08-29", "СУМА 42.00"), today).date,
        )
    }

    @Test fun `reads a two-digit year as this century`() {
        assertEquals(
            LocalDate(2026, 8, 29),
            ReceiptParser.parse(listOf("29/08/26"), today).date,
        )
    }

    @Test fun `ignores a date in the future`() {
        // A misread digit turns a date into a receipt from next year; the till number and
        // the cashier id misread into dates too.
        assertNull(ReceiptParser.parse(listOf("29.08.2027"), today).date)
    }

    @Test fun `ignores a date too old to be a receipt being photographed`() {
        assertNull(ReceiptParser.parse(listOf("29.08.2015"), today).date)
    }

    @Test fun `ignores an impossible date`() {
        assertNull(ReceiptParser.parse(listOf("32.13.2026"), today).date)
    }

    // -- merchant ------------------------------------------------------------------------

    @Test fun `takes the shop name from the top`() {
        val receipt = ReceiptParser.parse(
            listOf("ATB MARKET", "TOB ATB-MAPKET", "IПН 1234567890", "СУМА 42.00"),
            today,
        )
        assertEquals("ATB MARKET", receipt.merchant)
    }

    @Test fun `skips digit-heavy lines above the shop name`() {
        val receipt = ReceiptParser.parse(
            listOf("  ", "1234567890", "12-34-56", "SILPO", "СУМА 42.00"),
            today,
        )
        assertEquals("SILPO", receipt.merchant)
    }

    @Test fun `does not offer the total line as a shop name`() {
        assertNull(ReceiptParser.parse(listOf("СУМА 42.00"), today).merchant)
    }

    // -- whole result --------------------------------------------------------------------

    @Test fun `reads a whole receipt`() {
        val receipt = ReceiptParser.parse(
            """
            SILPO
            TOB CIЛЬПO-ФУД
            29.08.2026 19:03
            Milk 2.5% 1 x 42.90
            Bread 2 x 24.50
            СУМА 91.90
            ГОТІВКА 100.00
            РЕШТА 8.10
            """.trimIndent(),
            today,
        )
        assertEquals("91.90", receipt.total)
        assertEquals(LocalDate(2026, 8, 29), receipt.date)
        assertEquals("SILPO", receipt.merchant)
        assertTrue(!receipt.isEmpty)
    }

    @Test fun `a photo of nothing yields nothing`() {
        assertTrue(ReceiptParser.parse(emptyList(), today).isEmpty)
    }
}
