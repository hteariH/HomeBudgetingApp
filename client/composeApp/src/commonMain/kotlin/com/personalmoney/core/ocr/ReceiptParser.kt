package com.personalmoney.core.ocr

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Turns the lines an OCR engine read off a receipt into the three fields the entry form
 * wants: total, date, merchant.
 *
 * Deliberately in `commonMain` and free of any platform type - it takes a list of strings
 * and returns a value. That is what makes it testable without a camera, and what makes the
 * recognition engine swappable: [ReceiptScanner] can be re-actualised on top of a different
 * OCR library (one that reads Cyrillic, say) without a line of this file changing.
 *
 * ## Why the total is found the way it is
 *
 * The primary rule does not depend on script at all: **the total is the largest money-shaped
 * number on the receipt.** Line items, VAT, change and the cash tendered are all smaller
 * than, or equal to, the total. That rule alone gets the common case right whatever the
 * engine made of the words.
 *
 * Keywords are a refinement on top - when a line reads as a total, only the amounts near it
 * are considered, which is what keeps a large loyalty-points figure or the cash tendered from
 * winning. They are matched **twice**, because a receipt word arrives in one of two shapes:
 *
 * - as itself, once the engine has read the Cyrillic - `СУМА`, `ВСЬОГО`, `ДО СПЛАТИ`;
 * - as Latin lookalikes, because `eng` is in the language list and `СУМА` is four letters
 *   that each have a Latin twin, so it can still come back as `CYMA`.
 *
 * The second shape used to be the only one there was, back when this ran on ML Kit, which
 * ships no Cyrillic model at all. It stays because misreads did not stop with the engine
 * change - they only became the exception rather than the rule.
 *
 * None of this is trusted: everything found here lands in an editable form, never in a
 * saved transaction.
 */
object ReceiptParser {

    /**
     * Cyrillic letters that a recogniser can read as the Latin letter of the same shape.
     * Folding them back is what lets [LATIN_TOTAL_KEYWORDS] match a misread line.
     *
     * Only the unambiguous lookalikes are here. Д, Л, Г, И, Ж and the rest have no Latin
     * twin and come back as whatever the model guessed, so no mapping would help - which is
     * why [CYRILLIC_TOTAL_KEYWORDS] exists for the words that contain them.
     */
    private val HOMOGLYPHS: Map<Char, Char> = mapOf(
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K', 'М' to 'M', 'Н' to 'H',
        'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'У' to 'Y', 'Х' to 'X',
        'І' to 'I', 'Ї' to 'I',
    )

    /**
     * Written the way a *misread* line renders, not the way the word is spelled: `СУМА`
     * folds to `CYMA`, `СУММА` to `CYMMA`. Spelling those in Cyrillic here would look
     * correct and match nothing - the Cyrillic spellings live below.
     */
    private val LATIN_TOTAL_KEYWORDS =
        listOf("TOTAL", "CYMA", "CYMMA", "SUMA", "SUM", "AMOUNT", "PAID")

    /**
     * The same words as the engine actually reads them now that it knows Cyrillic. Includes
     * the ones no fold can reach, because Д, Л, Г and И have no Latin twin: `ВСЬОГО` and
     * `ДО СПЛАТИ` are how Ukrainian tills label the total at least as often as `СУМА`.
     */
    private val CYRILLIC_TOTAL_KEYWORDS = listOf(
        "СУМА", "СУММА", "ВСЬОГО", "ВСЕГО", "РАЗОМ", "ПІДСУМОК",
        "ІТОГО", "ИТОГО", "ДОСПЛАТИ", "КОПЛАТЕ",
    )

    /**
     * A money-shaped number: optional space-separated thousands, then exactly two decimals.
     *
     * The lookarounds are what keep dates out. In `03.09.2026` the engine cannot take
     * `03.09` (a digit follows the second group) nor `09.20` (a `.` precedes the first), so
     * a date contributes no candidates and needs no special case.
     */
    private val MONEY = Regex("""(?<![\d.,])(\d{1,3}(?:[ \u00A0]\d{3})+|\d+)[.,](\d{2})(?![\d.,])""")

    private val ISO_DATE = Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""")
    private val DMY_DATE = Regex("""(?<!\d)(\d{1,2})[./](\d{1,2})[./](\d{2}|\d{4})(?!\d)""")

    /** How far back a date on a receipt is still believable rather than a misread. */
    private const val MAX_AGE_YEARS = 5

    /** Lines to look at for a shop name. Below this it is address, tax id and item rows. */
    private const val MERCHANT_SEARCH_LINES = 6
    private const val MERCHANT_MAX_LENGTH = 40

    fun parse(lines: List<String>, today: LocalDate = currentDate()): ScannedReceipt =
        ScannedReceipt(
            total = findTotal(lines),
            date = findDate(lines, today),
            merchant = findMerchant(lines),
        )

    /** Convenience for engines that hand back one blob rather than lines. */
    fun parse(text: String, today: LocalDate = currentDate()): ScannedReceipt =
        parse(text.split('\n'), today)

    // -- total ---------------------------------------------------------------------------

    private class Amount(val cents: Long, val text: String, val line: Int)

    private fun findTotal(lines: List<String>): String? {
        val amounts = mutableListOf<Amount>()
        val vouchedLines = mutableSetOf<Int>()

        lines.forEachIndexed { index, line ->
            val onThisLine = MONEY.findAll(line).mapNotNull { match ->
                val whole = match.groupValues[1].filter { it.isDigit() }
                val fraction = match.groupValues[2]
                val cents = (whole + fraction).toLongOrNull() ?: return@mapNotNull null
                Amount(cents, "$whole.$fraction", index)
            }.toList()
            amounts += onThisLine

            if (looksLikeTotalLine(line)) {
                // The keyword vouches for its own line - unless it stands alone, which is
                // what a two-column receipt looks like once the recogniser has reported each
                // column as its own block: "СУМА", then "249.90" on the line below. Vouching
                // for both regardless would let the cash tendered on the next line win.
                if (onThisLine.isEmpty()) vouchedLines += index + 1 else vouchedLines += index
            }
        }

        if (amounts.isEmpty()) return null
        // Prefer the amounts a keyword vouches for; fall back to the whole receipt. Within
        // either set the largest wins - a "СУМА" block lists the net and the gross, and the
        // gross is what left the account.
        val vouched = amounts.filter { it.line in vouchedLines }
        return (vouched.ifEmpty { amounts }).maxBy { it.cents }.text
    }

    // -- date ----------------------------------------------------------------------------

    private fun findDate(lines: List<String>, today: LocalDate): LocalDate? {
        // 1 January rather than the same day five years back, which is not a date every
        // fifth year (29 February) and would throw.
        val earliest = LocalDate(today.year - MAX_AGE_YEARS, 1, 1)
        for (line in lines) {
            ISO_DATE.findAll(line).forEach { m ->
                val (y, mo, d) = m.destructured
                dateOrNull(y.toInt(), mo.toInt(), d.toInt())
                    ?.takeIf { it in earliest..today }
                    ?.let { return it }
            }
            DMY_DATE.findAll(line).forEach { m ->
                val (d, mo, y) = m.destructured
                // A two-digit year is this century: a receipt from 1998 is not a thing that
                // gets photographed into a budgeting app.
                val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
                dateOrNull(year, mo.toInt(), d.toInt())
                    ?.takeIf { it in earliest..today }
                    ?.let { return it }
            }
        }
        return null
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate(year, month, day) }.getOrNull()

    // -- merchant ------------------------------------------------------------------------

    private fun findMerchant(lines: List<String>): String? =
        lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MERCHANT_SEARCH_LINES)
            .firstOrNull { line ->
                val letters = line.count { it.isLetter() }
                val digits = line.count { it.isDigit() }
                // A shop name is mostly letters. A tax id, a receipt number and a till
                // address are mostly digits, and they sit in the same part of the receipt.
                letters >= 3 && letters > digits &&
                    line.length <= MERCHANT_MAX_LENGTH &&
                    !looksLikeTotalLine(line)
            }

    // -- shared --------------------------------------------------------------------------

    /**
     * True when the line labels a total, whichever of the two shapes it arrived in.
     */
    private fun looksLikeTotalLine(line: String): Boolean {
        val upper = line.uppercase()
        // Letters only, so that "ДО СПЛАТИ" and "ДО  СПЛАТИ:" both reach one spelling.
        val letters = upper.filter { it.isLetter() }
        if (CYRILLIC_TOTAL_KEYWORDS.any { letters.contains(it) }) return true
        val folded = buildString {
            for (raw in upper) {
                val char = HOMOGLYPHS[raw] ?: raw
                if (char in 'A'..'Z') append(char)
            }
        }
        return LATIN_TOTAL_KEYWORDS.any { folded.contains(it) }
    }

    private fun currentDate(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
