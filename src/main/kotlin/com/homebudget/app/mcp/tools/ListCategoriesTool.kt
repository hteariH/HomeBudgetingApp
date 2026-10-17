package com.homebudget.app.mcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.mcp.McpTool
import com.homebudget.app.mcp.ToolInputException
import com.homebudget.app.mcp.optionalString
import com.homebudget.app.mcp.toolResult
import com.homebudget.app.service.CategoryService
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ListCategoriesTool(
    private val categories: CategoryService,
    private val mapper: ObjectMapper
) : McpTool {

    data class Item(val uuid: String, val name: String, val type: CategoryType, val parentUuid: String?)

    override val name = "list_categories"
    override val description =
        "Lists the household's categories (not archived). A sub-category names its parent's uuid. " +
            "Pass a category's uuid or exact name to other tools."
    override val inputSchema = """
        {
          "type": "object",
          "properties": {
            "type": { "type": "string", "enum": ["INCOME", "EXPENSE"], "description": "Only this type (default both)" }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    @Transactional(readOnly = true)
    override fun call(args: Map<String, Any?>): CallToolResult {
        val type = args.optionalString("type")?.let { value ->
            CategoryType.entries.firstOrNull { it.name == value }
                ?: throw ToolInputException("\"type\" must be INCOME or EXPENSE")
        }
        val items = categories.findAll(type, includeArchived = false)
            .sortedWith(compareBy({ it.type }, { it.sortOrder }, { it.id }))
            .map { Item(it.uuid, it.name, it.type, it.parent?.uuid) }
        return toolResult(mapper, items)
    }
}
