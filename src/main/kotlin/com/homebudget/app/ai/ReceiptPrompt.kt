package com.homebudget.app.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDate

/** What the model is asked, and how its answer is read. */
object ReceiptPrompt {

    const val REMINDER = "Reply with the JSON object only."

    private val mapper = ObjectMapper()

    fun text(context: ExtractionContext): String = buildString {
        appendLine("Read the shop receipt in the image and reply with only a JSON object of this shape, with no other text:")
        appendLine(
            """{"merchant": string or null, "date": "YYYY-MM-DD" or null, "currency": ISO 4217 code or null, """ +
                """"total": decimal string or null, "items": [{"name": string, "quantity": decimal string or null, """ +
                """"price": decimal string}], "suggestedCategory": string or null}"""
        )
        appendLine("- merchant: the shop's name as printed.")
        appendLine("- date: the purchase date.")
        appendLine("- total: the amount paid; use a dot as the decimal separator and no currency sign.")
        appendLine("- items: every purchased line; price is the line's total, quantity only when printed.")
        if (context.currencies.isNotEmpty()) {
            appendLine("- currency: one of ${context.currencies.joinToString(", ")} when it matches the receipt, otherwise the code you see or null.")
        } else {
            appendLine("- currency: the code of the receipt's currency, or null when unclear.")
        }
        if (context.expenseCategories.isNotEmpty()) {
            appendLine("- suggestedCategory: the best fit from this list, written exactly as listed, or null: " +
                context.expenseCategories.joinToString(", ") { "\"$it\"" } + ".")
        } else {
            appendLine("- suggestedCategory: a short spending category such as \"Groceries\", or null.")
        }
        append("Use null for anything you cannot read.")
    }

    /**
     * The receipt in a model's answer: code fences and text around the object are ignored,
     * numbers may come as numbers or strings, an unreadable date or amount becomes null.
     *
     * @throws ExtractionException when there is no JSON object or it has the wrong shape.
     */
    fun parse(text: String): ExtractedReceipt {
        val root = objectIn(text) ?: throw unreadable()
        val items = root["items"]
        if (items != null && !items.isNull && !items.isArray) throw unreadable()
        return ExtractedReceipt(
            merchant = string(root["merchant"]),
            date = string(root["date"])?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            currency = string(root["currency"])?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{3}")) },
            total = decimal(root["total"]),
            items = items?.filter { it.isObject }?.mapNotNull { item ->
                val name = string(item["name"]) ?: return@mapNotNull null
                val price = decimal(item["price"]) ?: return@mapNotNull null
                ExtractedItem(name, decimal(item["quantity"]), price)
            } ?: emptyList(),
            suggestedCategory = string(root["suggestedCategory"])
        )
    }

    private fun unreadable() = ExtractionException(ExtractionException.UNREADABLE)

    private fun objectIn(text: String): JsonNode? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { mapper.readTree(text.substring(start, end + 1)) }.getOrNull()?.takeIf { it.isObject }
    }

    private fun string(node: JsonNode?): String? =
        node?.takeIf { it.isTextual || it.isNumber }?.asText()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun decimal(node: JsonNode?): BigDecimal? = when {
        node == null || node.isNull -> null
        node.isNumber -> node.decimalValue()
        node.isTextual -> decimalOf(node.asText())
        else -> null
    }

    /**
     * "12,40 €" → 12.40, "1 234.50" → 1234.50, "1,234" → 1234. With both separators the last
     * one is the decimal point; one that repeats separates thousands, and so does a single
     * comma before exactly three digits that do not follow a lone zero. A single dot is
     * always the decimal point - the prompt asks for one.
     */
    private fun decimalOf(raw: String): BigDecimal? {
        val s = raw.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        val commas = s.count { it == ',' }
        val dots = s.count { it == '.' }
        val normal = when {
            commas > 0 && dots > 0 ->
                if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            commas > 1 -> s.replace(",", "")
            dots > 1 -> s.replace(".", "")
            THOUSANDS.matches(s) -> s.replace(",", "")
            else -> s.replace(',', '.')
        }
        return normal.toBigDecimalOrNull()
    }

    private val THOUSANDS = Regex("""-?[1-9]\d{0,2},\d{3}""")
}
