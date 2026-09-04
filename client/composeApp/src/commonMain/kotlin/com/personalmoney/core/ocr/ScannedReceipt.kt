package com.personalmoney.core.ocr

import kotlinx.datetime.LocalDate

/**
 * What could be read off a photographed receipt. Every field is optional, because OCR on a
 * crumpled thermal print fails one field at a time rather than all at once.
 *
 * [total] is a plain decimal string - `"249.90"`, never `"1 249,90"` - because that is what
 * [com.personalmoney.feature.quickadd.QuickAddViewModel.setAmountText] wants: the amount
 * lands in the same field the user types into, so a misread price can be corrected rather
 * than cleared and retyped. Handing over a `Long` of minor units instead would mean parsing
 * the currency here, and the parser has no idea which account the transaction will land on.
 *
 * A scan is never saved on the user's behalf - it fills the form and stops.
 */
data class ScannedReceipt(
    val total: String? = null,
    val date: LocalDate? = null,
    val merchant: String? = null,
) {
    /** True when the photo yielded nothing worth putting in the form. */
    val isEmpty: Boolean get() = total == null && date == null && merchant == null
}
