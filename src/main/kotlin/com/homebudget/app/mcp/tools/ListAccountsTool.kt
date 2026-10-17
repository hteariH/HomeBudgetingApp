package com.homebudget.app.mcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.AccountType
import com.homebudget.app.domain.MinorUnits
import com.homebudget.app.mcp.McpTool
import com.homebudget.app.mcp.optionalBoolean
import com.homebudget.app.mcp.toolResult
import com.homebudget.app.service.AccountService
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ListAccountsTool(
    private val accounts: AccountService,
    private val mapper: ObjectMapper
) : McpTool {

    data class Item(
        val uuid: String,
        val name: String,
        val type: AccountType,
        val currency: String,
        /** Current balance as a decimal string, as `/api/accounts` reports it. */
        val balance: String,
        val archived: Boolean
    )

    override val name = "list_accounts"
    override val description =
        "Lists the household's accounts with their currency and current balance. " +
            "Pass an account's uuid or exact name to other tools."
    override val inputSchema = """
        {
          "type": "object",
          "properties": {
            "includeArchived": { "type": "boolean", "description": "Also list archived accounts (default false)" }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    @Transactional(readOnly = true)
    override fun call(args: Map<String, Any?>): CallToolResult {
        val includeArchived = args.optionalBoolean("includeArchived") ?: false
        val balances = accounts.balances()
        val items = accounts.findAll()
            .filter { includeArchived || !it.archived }
            .sortedWith(compareBy({ it.archived }, { it.sortOrder }, { it.id }))
            .map {
                Item(
                    uuid = it.uuid,
                    name = it.name,
                    type = it.type,
                    currency = it.currency.code,
                    balance = MinorUnits.toDecimal(balances[it.id] ?: 0L, it.currency.decimals).toPlainString(),
                    archived = it.archived
                )
            }
        return toolResult(mapper, items)
    }
}
