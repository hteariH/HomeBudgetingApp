package com.homebudget.app.mcp.tools

import com.homebudget.app.domain.Account
import com.homebudget.app.domain.Category
import com.homebudget.app.domain.CategoryType
import com.homebudget.app.mcp.ToolInputException
import com.homebudget.app.service.AccountService
import com.homebudget.app.service.CategoryService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Resolves what an assistant calls an account or a category - a uuid, or an exact name in
 * any case - within the signed-in household. An unknown or ambiguous name is a
 * [ToolInputException] that tells the assistant what to do next.
 */
@Component
@Transactional(readOnly = true)
class References(
    private val accounts: AccountService,
    private val categories: CategoryService
) {
    /** Archived accounts are left out unless [includeArchived]: they cannot take new entries. */
    fun account(reference: String, includeArchived: Boolean = false): Account {
        val ref = reference.trim()
        val candidates = accounts.findAll()
            .filter { includeArchived || !it.archived }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        candidates.firstOrNull { it.uuid == ref }?.let { return it }
        val named = candidates.filter { it.name.equals(ref, ignoreCase = true) }
        return when (named.size) {
            1 -> named.single()
            0 -> throw ToolInputException("Account \"$ref\" not found — list_accounts shows the accounts")
            else -> throw ToolInputException(
                "Account \"$ref\" matches ${named.size} accounts: " +
                    named.joinToString(", ") { "${it.name} (${it.currency.code}) ${it.uuid}" } +
                    " — pass the uuid"
            )
        }
    }

    /**
     * A non-archived category (or any, with [includeArchived], for reading history). With a [type], one of the other type is refused by name
     * ("Category "Salary" is an income category") rather than reported as missing.
     */
    fun category(reference: String, type: CategoryType? = null, includeArchived: Boolean = false): Category {
        val ref = reference.trim()
        val all = categories.findAll(includeArchived = includeArchived).sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val byUuid = all.firstOrNull { it.uuid == ref }
        val named = if (byUuid != null) listOf(byUuid) else all.filter { it.name.equals(ref, ignoreCase = true) }
        val matching = if (type == null) named else named.filter { it.type == type }
        return when {
            matching.size == 1 -> matching.single()
            matching.size > 1 -> throw ToolInputException(
                "Category \"$ref\" matches ${matching.size} categories: " +
                    matching.joinToString(", ") { "${it.name} (${it.type}) ${it.uuid}" } +
                    " — pass the uuid"
            )
            named.isNotEmpty() -> throw ToolInputException(
                "Category \"${named.first().name}\" is an ${named.first().type.name.lowercase()} category"
            )
            else -> throw ToolInputException("Category \"$ref\" not found — list_categories shows the categories")
        }
    }
}
