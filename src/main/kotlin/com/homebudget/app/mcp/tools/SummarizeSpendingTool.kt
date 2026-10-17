package com.homebudget.app.mcp.tools

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.TransactionType
import com.homebudget.app.mcp.McpTool
import com.homebudget.app.mcp.ToolInputException
import com.homebudget.app.mcp.optionalString
import com.homebudget.app.mcp.requiredDate
import com.homebudget.app.mcp.toolResult
import com.homebudget.app.service.SpendingSummaryService
import com.homebudget.app.service.SpendingSummaryService.GroupBy
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.stereotype.Component

@Component
class SummarizeSpendingTool(
    private val summary: SpendingSummaryService,
    private val mapper: ObjectMapper
) : McpTool {

    data class Total(val currency: String, val amount: String)

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Key(val uuid: String?, val name: String)

    data class GroupOut(val key: Any, val totals: List<Total>)

    data class Output(val groups: List<GroupOut>, val totals: List<Total>)

    override val name = "summarize_spending"
    override val description =
        "Totals spending (or income) over a date range, grouped by category, account or month. " +
            "Each total is per currency - currencies are never added together or converted - so groups are ordered by " +
            "their largest single-currency total. Transfers are not counted."
    override val inputSchema = """
        {
          "type": "object",
          "properties": {
            "from": { "type": "string", "description": "First day, YYYY-MM-DD" },
            "to": { "type": "string", "description": "Last day, YYYY-MM-DD (inclusive); at most 5 years after from" },
            "groupBy": { "type": "string", "enum": ["CATEGORY", "ACCOUNT", "MONTH"], "description": "Default CATEGORY" },
            "type": { "type": "string", "enum": ["EXPENSE", "INCOME"], "description": "Default EXPENSE" }
          },
          "required": ["from", "to"],
          "additionalProperties": false
        }
    """.trimIndent()

    override fun call(args: Map<String, Any?>): CallToolResult {
        val from = args.requiredDate("from")
        val to = args.requiredDate("to")
        if (from.isAfter(to)) throw ToolInputException("\"from\" must not be after \"to\"")
        if (to.isAfter(from.plusYears(5))) throw ToolInputException("The range must not exceed 5 years")
        val groupBy = when (val v = args.optionalString("groupBy")?.uppercase()) {
            null -> GroupBy.CATEGORY
            else -> GroupBy.entries.firstOrNull { it.name == v }
                ?: throw ToolInputException("\"groupBy\" must be CATEGORY, ACCOUNT or MONTH")
        }
        val type = when (val v = args.optionalString("type")?.uppercase()) {
            null, "EXPENSE" -> TransactionType.EXPENSE
            "INCOME" -> TransactionType.INCOME
            else -> throw ToolInputException("\"type\" must be EXPENSE or INCOME")
        }
        val result = summary.summarize(from, to, groupBy, type)
        fun totals(m: Map<String, java.math.BigDecimal>) = m.map { Total(it.key, it.value.toPlainString()) }
        return toolResult(
            mapper,
            Output(
                groups = result.groups.map {
                    GroupOut(if (groupBy == GroupBy.MONTH) it.monthKey!! else Key(it.uuid, it.name), totals(it.totals))
                },
                totals = totals(result.totals)
            )
        )
    }
}
