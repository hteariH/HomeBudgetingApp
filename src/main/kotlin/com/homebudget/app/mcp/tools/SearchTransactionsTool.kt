package com.homebudget.app.mcp.tools

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.Account
import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.mcp.McpTool
import com.homebudget.app.mcp.ToolInputException
import com.homebudget.app.mcp.optionalDate
import com.homebudget.app.mcp.optionalDecimal
import com.homebudget.app.mcp.optionalString
import com.homebudget.app.mcp.toolResult
import com.homebudget.app.service.SpendingSummaryService
import com.homebudget.app.service.SpendingSummaryService.Entry
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset

@Component
class SearchTransactionsTool(
    private val summary: SpendingSummaryService,
    private val references: References,
    private val mapper: ObjectMapper
) : McpTool {

    data class Ref(val uuid: String, val name: String)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class ItemOut(val name: String, val quantity: String?, val price: String)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class ReceiptOut(val merchant: String?, val total: String?, val items: List<ItemOut>)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Item(
        val uuid: String,
        val date: String,
        val type: TransactionType,
        val account: Ref,
        val toAccount: Ref?,
        val category: Ref?,
        val amount: String,
        val currency: String,
        val toAmount: String?,
        val toCurrency: String?,
        val note: String?,
        val author: String?,
        val source: String,
        val receipt: ReceiptOut?
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Output(val transactions: List<Item>, val nextCursor: String?)

    override val name = "search_transactions"
    override val description =
        "Finds transactions, newest first. Filter by date range, account, category, type, text in the note or " +
            "receipt merchant, and amount (absolute value, in the transaction's own currency). " +
            "A transfer is one entry with toAccount and toAmount. Pass nextCursor back as cursor for the next page."
    override val inputSchema = """
        {
          "type": "object",
          "properties": {
            "from": { "type": "string", "description": "First day, YYYY-MM-DD" },
            "to": { "type": "string", "description": "Last day, YYYY-MM-DD (inclusive)" },
            "account": { "type": "string", "description": "Account uuid or exact name" },
            "category": { "type": "string", "description": "Category uuid or exact name" },
            "type": { "type": "string", "enum": ["INCOME", "EXPENSE", "TRANSFER"] },
            "text": { "type": "string", "description": "Case-insensitive substring of the note or the receipt's merchant" },
            "minAmount": { "type": "string", "description": "Decimal string, compared on absolute value" },
            "maxAmount": { "type": "string", "description": "Decimal string, compared on absolute value" },
            "limit": { "type": "integer", "minimum": 1, "maximum": 100, "description": "Default 50" },
            "cursor": { "type": "string", "description": "nextCursor of the previous page" }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    @Transactional(readOnly = true)
    override fun call(args: Map<String, Any?>): CallToolResult {
        val from = args.optionalDate("from")
        val to = args.optionalDate("to")
        if (from != null && to != null && from.isAfter(to)) throw ToolInputException("\"from\" must not be after \"to\"")
        val type = args.optionalString("type")?.uppercase()?.let { v ->
            TransactionType.entries.firstOrNull { it.name == v }
                ?: throw ToolInputException("\"type\" must be INCOME, EXPENSE or TRANSFER")
        }
        val limit = when (val v = args["limit"]) {
            null -> 50
            is Number -> v.toInt().also { if (it !in 1..100) throw ToolInputException("\"limit\" must be between 1 and 100") }
            else -> throw ToolInputException("\"limit\" must be a whole number between 1 and 100")
        }
        val criteria = SpendingSummaryService.SearchCriteria(
            from = from,
            to = to,
            accountId = args.optionalString("account")?.let { references.account(it, includeArchived = true).id },
            categoryId = args.optionalString("category")?.let { references.category(it).id },
            type = type,
            text = args.optionalString("text")?.trim(),
            minAmount = args.optionalDecimal("minAmount"),
            maxAmount = args.optionalDecimal("maxAmount")
        )
        val page = try {
            summary.search(criteria, limit, args.optionalString("cursor"))
        } catch (e: IllegalArgumentException) {
            throw ToolInputException(e.message ?: "Invalid input")
        }
        val decimals = summary.decimals()
        return toolResult(mapper, Output(page.entries.map { item(it, decimals) }, page.nextCursor))
    }

    private fun ref(a: Account) = Ref(a.uuid, a.name)

    private fun item(e: Entry, decimals: Map<String, Int>): Item {
        val t = e.transaction
        fun money(minor: Long, currency: String, fallback: Int) =
            MinorUnits.toDecimal(minor, decimals[currency] ?: fallback).toPlainString()
        val transfer = t.type == TransactionType.TRANSFER
        val receipt = e.receipt?.let { r ->
            val code = r.currencyCode ?: t.currencyCode
            val d = decimals[code] ?: 2
            ReceiptOut(
                merchant = r.merchant,
                total = r.totalMinor?.let { MinorUnits.toDecimal(it, d).toPlainString() },
                items = r.items.map {
                    ItemOut(it.name, it.quantity?.stripTrailingZeros()?.toPlainString(), MinorUnits.toDecimal(it.priceMinor, d).toPlainString())
                }
            )
        }
        return Item(
            uuid = t.uuid,
            date = t.occurredAt.atZone(ZoneOffset.UTC).toLocalDate().toString(),
            type = t.type,
            account = ref(t.account),
            toAccount = if (transfer) e.credit?.account?.let(::ref) else null,
            category = t.category?.let { Ref(it.uuid, it.name) },
            amount = money(Math.abs(t.amountMinor), t.currencyCode, t.account.currency.decimals),
            currency = t.currencyCode,
            toAmount = e.credit?.let { money(it.amountMinor, it.currencyCode, it.account.currency.decimals) },
            toCurrency = e.credit?.currencyCode,
            note = t.description,
            author = t.createdBy?.let { "${it.firstName} ${it.lastName}".trim() },
            source = e.source,
            receipt = receipt
        )
    }
}
