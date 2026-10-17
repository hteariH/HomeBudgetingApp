package com.homebudget.app.mcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.domain.Currency
import com.homebudget.app.domain.Transaction
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.dto.CreateTransactionRequest
import com.homebudget.app.mcp.McpTool
import com.homebudget.app.mcp.ToolInputException
import com.homebudget.app.mcp.minorUnits
import com.homebudget.app.mcp.optionalDate
import com.homebudget.app.mcp.optionalDecimal
import com.homebudget.app.mcp.optionalString
import com.homebudget.app.mcp.optionalText
import com.homebudget.app.mcp.positiveMinorUnits
import com.homebudget.app.mcp.requiredDecimal
import com.homebudget.app.mcp.requiredString
import com.homebudget.app.mcp.toolResult
import com.homebudget.app.receipt.AssistantReceiptItem
import com.homebudget.app.receipt.ReceiptService
import com.homebudget.app.security.SecurityUtils
import com.homebudget.app.service.SpendingSummaryService
import com.homebudget.app.service.TransactionService
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate

/**
 * Records an income or an expense, with the receipt the assistant read off a photo when it
 * sends one. The entry is the token owner's, marked `MCP`.
 */
@Component
class CreateTransactionTool(
    private val references: References,
    private val transactions: TransactionService,
    private val receipts: ReceiptService,
    private val summary: SpendingSummaryService,
    private val securityUtils: SecurityUtils,
    private val clock: Clock,
    private val mapper: ObjectMapper
) : McpTool {

    companion object {
        const val MAX_NOTE = 500
        const val MAX_MERCHANT = 255
        const val MAX_ITEM_NAME = 255
        const val MAX_ITEMS = 200
        private val MAX_QUANTITY = BigDecimal("99999999999.999") // the column: 14 digits, 3 after the point
    }

    private data class ReceiptInput(val merchant: String?, val totalMinor: Long?, val items: List<AssistantReceiptItem>)

    override val name = "create_transaction"
    override val description =
        "Records an income or an expense on an account, in the account's currency. The category must be of the same " +
            "type. Pass receipt with the merchant and line items when you read them off a receipt; the receipt is " +
            "saved with the transaction (without a photo). Use create_transfer to move money between accounts."
    override val inputSchema = """
        {
          "type": "object",
          "properties": {
            "account": { "type": "string", "description": "Account uuid or exact name; not an archived one" },
            "type": { "type": "string", "enum": ["INCOME", "EXPENSE"] },
            "amount": { "type": "string", "description": "Positive decimal string in the account's currency, e.g. 125.50" },
            "date": { "type": "string", "description": "YYYY-MM-DD; default today (UTC)" },
            "category": { "type": "string", "description": "Category uuid or exact name, of the same type" },
            "note": { "type": "string", "maxLength": $MAX_NOTE },
            "receipt": {
              "type": "object",
              "properties": {
                "merchant": { "type": "string", "maxLength": $MAX_MERCHANT },
                "total": { "type": "string", "description": "Positive decimal string; default the items' sum. Items or a total are required" },
                "items": {
                  "type": "array",
                  "maxItems": $MAX_ITEMS,
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string", "minLength": 1, "maxLength": $MAX_ITEM_NAME },
                      "quantity": { "type": "string", "description": "Decimal string" },
                      "price": { "type": "string", "description": "Decimal string: the line's price; negative for a discount" }
                    },
                    "required": ["name", "price"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["items"],
              "additionalProperties": false
            }
          },
          "required": ["account", "type", "amount"],
          "additionalProperties": false
        }
    """.trimIndent()

    @Transactional
    override fun call(args: Map<String, Any?>): CallToolResult {
        val account = references.accountForNewEntry(args.requiredString("account"))
        val type = when (args.requiredString("type").trim().uppercase()) {
            "INCOME" -> TransactionType.INCOME
            "EXPENSE" -> TransactionType.EXPENSE
            else -> throw ToolInputException("\"type\" must be INCOME or EXPENSE")
        }
        val amount = args.requiredDecimal("amount")
        positiveMinorUnits("amount", amount, account.currency)
        val date = args.optionalDate("date") ?: LocalDate.now(clock)
        val category = args.optionalString("category")?.let { references.category(it, CategoryType.valueOf(type.name)) }
        val note = args.optionalText("note", MAX_NOTE)
        // Everything is checked before anything is written.
        val receiptInput = args["receipt"]?.let { receiptInput(it, account.currency) }

        val receipt = receiptInput?.let {
            receipts.createFromAssistant(
                securityUtils.getCurrentHousehold().id, securityUtils.getCurrentUser().id,
                it.merchant, date, account.currency.code, it.totalMinor, it.items
            )
        }
        val saved = transactions.create(
            CreateTransactionRequest(
                accountId = account.id, categoryId = category?.id, amount = amount, type = type, date = date,
                description = note, receiptUuid = receipt?.uuid
            ),
            source = Transaction.SOURCE_MCP
        )
        return toolResult(mapper, SearchTransactionsTool.item(summary.entries(listOf(saved)).single(), summary.decimals()))
    }

    private fun receiptInput(value: Any, currency: Currency): ReceiptInput {
        val receipt = value.asObject("receipt")
        val merchant = receipt.optionalText("merchant", MAX_MERCHANT, "receipt.merchant")
        val total = receipt.optionalDecimal("total", "receipt.total")?.let { positiveMinorUnits("receipt.total", it, currency) }
        val rawItems = receipt["items"] as? List<*>
            ?: throw ToolInputException(if (receipt["items"] == null) "\"receipt.items\" is required" else "\"receipt.items\" must be an array")
        if (rawItems.size > MAX_ITEMS) throw ToolInputException("\"receipt.items\" must have at most $MAX_ITEMS items")
        val items = rawItems.mapIndexed { i, raw ->
            val label = "receipt.items[$i]"
            val item = raw?.asObject(label) ?: throw ToolInputException("\"$label\" must be an object")
            val name = item.optionalString("name", "$label.name")?.trim()
            if (name == null || name.length > MAX_ITEM_NAME) {
                throw ToolInputException("\"$label.name\" must be 1 to $MAX_ITEM_NAME characters")
            }
            val quantity = item.optionalDecimal("quantity", "$label.quantity")?.let {
                if (it.signum() <= 0 || it > MAX_QUANTITY) {
                    throw ToolInputException("\"$label.quantity\" must be greater than zero and at most $MAX_QUANTITY")
                }
                it.setScale(3, RoundingMode.HALF_UP)
            }
            val price = item.optionalDecimal("price", "$label.price") ?: throw ToolInputException("\"$label.price\" is required")
            AssistantReceiptItem(name, quantity, minorUnits("$label.price", price, currency))
        }
        if (items.isEmpty() && total == null) throw ToolInputException("A receipt needs items or a total")
        // The receipt keeps the sum and its difference from the total (see Receipt.checkTotal); both must fit.
        try {
            val sum = items.fold(0L) { acc, it -> Math.addExact(acc, it.priceMinor) }
            total?.let { Math.absExact(Math.subtractExact(sum, it)) }
        } catch (e: ArithmeticException) {
            throw ToolInputException("\"receipt.items\" add up to more than an amount can hold")
        }
        return ReceiptInput(merchant, total, items)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any.asObject(label: String): Map<String, Any?> =
        (this as? Map<String, Any?>) ?: throw ToolInputException("\"$label\" must be an object")
}
