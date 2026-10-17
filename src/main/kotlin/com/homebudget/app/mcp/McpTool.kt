package com.homebudget.app.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.spec.McpSchema.CallToolResult

/**
 * One MCP tool. Implementations are Spring beans; [McpServerConfig] registers every one.
 * [call] runs with the token owner signed in (see [McpPrincipalScope]), so services scope
 * by household exactly as they do for REST.
 */
interface McpTool {
    val name: String
    val description: String
    /** JSON Schema of the arguments object. */
    val inputSchema: String
    fun call(args: Map<String, Any?>): CallToolResult
}

/** A mistake in the assistant's input; [message] goes back to it as the tool error. */
class ToolInputException(message: String) : RuntimeException(message)

/**
 * The tool's answer: pretty JSON of [value] as the one text content, and the same value as
 * `structuredContent` when it is a JSON object (MCP allows only objects there, so a list
 * is text alone).
 */
fun toolResult(mapper: ObjectMapper, value: Any): CallToolResult {
    val tree = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(value)
    val builder = CallToolResult.builder()
        .addTextContent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree))
    if (tree.isObject) builder.structuredContent(mapper.convertValue(tree, Map::class.java))
    return builder.build()
}

/** An optional boolean argument. */
fun Map<String, Any?>.optionalBoolean(key: String): Boolean? = when (val v = this[key]) {
    null -> null
    is Boolean -> v
    else -> throw ToolInputException("\"$key\" must be true or false")
}

/** An optional string argument; blank counts as absent. [label] names it in errors (a nested field's path). */
fun Map<String, Any?>.optionalString(key: String, label: String = key): String? = when (val v = this[key]) {
    null -> null
    is String -> v.takeIf { it.isNotBlank() }
    else -> throw ToolInputException("\"$label\" must be a string")
}

/** An optional `YYYY-MM-DD` argument. */
fun Map<String, Any?>.optionalDate(key: String): java.time.LocalDate? =
    optionalString(key)?.let {
        try {
            java.time.LocalDate.parse(it.trim())
        } catch (e: java.time.format.DateTimeParseException) {
            throw ToolInputException("\"$key\" must be a date like 2026-03-31")
        }
    }

fun Map<String, Any?>.requiredDate(key: String): java.time.LocalDate =
    optionalDate(key) ?: throw ToolInputException("\"$key\" is required (YYYY-MM-DD)")

/** An optional money argument as a decimal string (a JSON number is accepted too). */
fun Map<String, Any?>.optionalDecimal(key: String, label: String = key): java.math.BigDecimal? = when (val v = this[key]) {
    null -> null
    is Number, is String -> try {
        java.math.BigDecimal(v.toString().trim())
    } catch (e: NumberFormatException) {
        throw ToolInputException("\"$label\" must be a decimal number like 12.50")
    }
    else -> throw ToolInputException("\"$label\" must be a decimal number like 12.50")
}

fun Map<String, Any?>.requiredString(key: String): String =
    optionalString(key) ?: throw ToolInputException("\"$key\" is required")

fun Map<String, Any?>.requiredDecimal(key: String): java.math.BigDecimal =
    optionalDecimal(key) ?: throw ToolInputException("\"$key\" is required")

/** An optional text argument of at most [max] characters, trimmed. */
fun Map<String, Any?>.optionalText(key: String, max: Int, label: String = key): String? =
    optionalString(key, label)?.trim()?.also {
        if (it.length > max) throw ToolInputException("\"$label\" must be at most $max characters")
    }

/**
 * [amount] in [currency]'s minor units. More decimals than the currency has is a mistake to
 * report, never something to round away.
 */
fun minorUnits(label: String, amount: java.math.BigDecimal, currency: com.homebudget.app.domain.Currency): Long {
    if (amount.stripTrailingZeros().scale() > currency.decimals) {
        throw ToolInputException("\"$label\" has more decimal places than ${currency.code} allows (${currency.decimals})")
    }
    return try {
        com.homebudget.app.domain.MinorUnits.toMinor(amount, currency.decimals)
    } catch (e: IllegalArgumentException) {
        throw ToolInputException("\"$label\" is too large")
    }
}

/** [minorUnits] of an amount that must be above zero. */
fun positiveMinorUnits(label: String, amount: java.math.BigDecimal, currency: com.homebudget.app.domain.Currency): Long {
    if (amount.signum() <= 0) throw ToolInputException("\"$label\" must be greater than zero")
    return minorUnits(label, amount, currency)
}
