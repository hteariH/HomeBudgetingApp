package com.homebudget.app.mcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.Transaction
import com.homebudget.app.dto.CreateTransferRequest
import com.homebudget.app.mcp.McpTool
import com.homebudget.app.mcp.ToolInputException
import com.homebudget.app.mcp.optionalDate
import com.homebudget.app.mcp.optionalDecimal
import com.homebudget.app.mcp.optionalText
import com.homebudget.app.mcp.positiveMinorUnits
import com.homebudget.app.mcp.requiredDecimal
import com.homebudget.app.mcp.requiredString
import com.homebudget.app.mcp.toolResult
import com.homebudget.app.service.SpendingSummaryService
import com.homebudget.app.service.TransferService
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/** Moves money between two of the household's accounts; the transfer is the token owner's, marked `MCP`. */
@Component
class CreateTransferTool(
    private val references: References,
    private val transfers: TransferService,
    private val summary: SpendingSummaryService,
    private val clock: Clock,
    private val mapper: ObjectMapper
) : McpTool {

    override val name = "create_transfer"
    override val description =
        "Moves money from one account to another. Between accounts in different currencies, pass toAmount: the " +
            "amount that arrives, in the destination account's currency."
    override val inputSchema = """
        {
          "type": "object",
          "properties": {
            "fromAccount": { "type": "string", "description": "Account uuid or exact name the money leaves" },
            "toAccount": { "type": "string", "description": "Account uuid or exact name the money arrives in" },
            "amount": { "type": "string", "description": "Positive decimal string in fromAccount's currency" },
            "toAmount": { "type": "string", "description": "Decimal string in toAccount's currency; required when the currencies differ" },
            "date": { "type": "string", "description": "YYYY-MM-DD; default today (UTC)" },
            "note": { "type": "string", "maxLength": ${CreateTransactionTool.MAX_NOTE} }
          },
          "required": ["fromAccount", "toAccount", "amount"],
          "additionalProperties": false
        }
    """.trimIndent()

    @Transactional
    override fun call(args: Map<String, Any?>): CallToolResult {
        val from = references.accountForNewEntry(args.requiredString("fromAccount"))
        val to = references.accountForNewEntry(args.requiredString("toAccount"))
        if (from.id == to.id) throw ToolInputException("fromAccount and toAccount must be different accounts")
        val amount = args.requiredDecimal("amount")
        positiveMinorUnits("amount", amount, from.currency)
        val given = args.optionalDecimal("toAmount")
        val toAmount = if (from.currency.code != to.currency.code) {
            given ?: throw ToolInputException(
                "The accounts use ${from.currency.code} and ${to.currency.code} — pass toAmount, the amount that arrives"
            )
            given.also { positiveMinorUnits("toAmount", it, to.currency) }
        } else {
            if (given != null && given.compareTo(amount) != 0) {
                throw ToolInputException("Both accounts use ${from.currency.code} — leave out toAmount or make it equal to amount")
            }
            amount
        }
        val date = args.optionalDate("date") ?: LocalDate.now(clock)
        val note = args.optionalText("note", CreateTransactionTool.MAX_NOTE)

        val debit = transfers.create(
            CreateTransferRequest(
                fromAccountId = from.id, toAccountId = to.id, fromAmount = amount, toAmount = toAmount,
                date = date, description = note
            ),
            source = Transaction.SOURCE_MCP
        )
        return toolResult(mapper, SearchTransactionsTool.item(summary.entries(listOf(debit)).single(), summary.decimals()))
    }
}
