package com.personalmoney.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one thing [ReceiptParserTest] cannot cover: that the OCR engine actually hands over the
 * text the parser is written against.
 *
 * The parser's own tests feed it strings, so they would stay green even if the engine returned
 * each receipt as one run-on block, or dropped the decimal point, or reported lines bottom-up.
 * These render a receipt, put it through the real engine on the device, and check that the
 * whole chain - engine, line order, parser - lands on the right amount.
 *
 * The Cyrillic case is the reason the engine was changed at all: under ML Kit it was not
 * merely inaccurate, it was impossible, because Google ships no Cyrillic model.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptRecognitionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate(2026, 9, 3)

    @Test
    fun readsALatinReceiptEndToEnd() {
        val lines = recognize(
            listOf(
                "SILPO",
                "Kyiv, Khreshchatyk 22",
                "29.08.2026 19:03",
                "Milk 2.5%       42.90",
                "Bread           24.50",
                "Coffee          24.50",
                "SUMA            91.90",
                "CASH           100.00",
                "CHANGE           8.10",
            )
        )

        val parsed = ReceiptParser.parse(lines, today)
        assertEquals("recognised: $lines", "91.90", parsed.total)
        assertEquals(LocalDate(2026, 8, 29), parsed.date)
        assertEquals("SILPO", parsed.merchant)
    }

    /**
     * A Ukrainian receipt, which is what this app is actually pointed at.
     *
     * The total assertion is the load-bearing one: `100.00` is the larger number on the page,
     * so `91.90` can only win if the engine read `СУМА` and the parser recognised it. That is
     * the whole engine change in one assertion.
     */
    @Test
    fun readsACyrillicReceiptEndToEnd() {
        val lines = recognize(
            listOf(
                "СІЛЬПО",
                "ТОВ СІЛЬПО-ФУД",
                "29.08.2026 19:03",
                "Молоко 2.5%     42.90",
                "Хліб            24.50",
                "Кава            24.50",
                "СУМА            91.90",
                "ГОТІВКА        100.00",
                "РЕШТА            8.10",
            ),
            typeface = Typeface.DEFAULT,
        )

        val parsed = ReceiptParser.parse(lines, today)
        assertEquals("recognised: $lines", "91.90", parsed.total)
        assertEquals(LocalDate(2026, 8, 29), parsed.date)
        // Not asserted character by character: a shop name is the field OCR is worst at, and
        // pinning its exact spelling would make this test about the font rather than the
        // pipeline. That it found *something* name-shaped at the top is the real claim.
        val merchant = parsed.merchant
        assertNotNull("no merchant in $lines", merchant)
        assertTrue("merchant was '$merchant'", merchant!!.count { it.isLetter() } >= 3)
    }

    private fun recognize(
        receipt: List<String>,
        typeface: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL),
    ): List<String> {
        val lines = runBlocking { recognizeLines(context, render(receipt, typeface)) }
        // Fails loudly rather than as a confusing null further down: everything after this
        // point is meaningless if the engine read nothing at all.
        assertTrue("recogniser returned no lines", lines.isNotEmpty())
        return lines
    }

    /**
     * A receipt as a bitmap: black text on white, at roughly the scale a phone camera captures
     * a paper receipt held at arm's length.
     */
    private fun render(lines: List<String>, typeface: Typeface): Bitmap {
        val padding = 48f
        val lineHeight = 64f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 44f
            this.typeface = typeface
        }
        val width = (2 * padding + lines.maxOf { paint.measureText(it) }).toInt()
        val height = (2 * padding + lines.size * lineHeight).toInt()

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.WHITE)
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, padding, padding + (index + 1) * lineHeight, paint)
            }
        }
    }
}
