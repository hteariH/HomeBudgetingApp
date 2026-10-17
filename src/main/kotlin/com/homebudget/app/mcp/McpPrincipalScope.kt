package com.homebudget.app.mcp

import com.homebudget.app.security.UserPrincipal
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Carries the token owner from the HTTP request to the tool.
 *
 * The SDK may run a tool handler off the servlet thread, so a tool never relies on that
 * thread's `SecurityContext`: [McpAuthFilter] puts the principal in the request attribute
 * [ATTRIBUTE], the transport's context extractor copies it into the MCP transport context,
 * and every call goes through [runAs], which signs the principal in for the call and signs
 * it out afterwards.
 */
object McpPrincipalScope {
    const val ATTRIBUTE = "com.homebudget.app.mcp.principal"

    fun <T> runAs(principal: UserPrincipal, block: () -> T): T {
        val previous = SecurityContextHolder.getContext()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        SecurityContextHolder.setContext(context)
        try {
            return block()
        } finally {
            // Clears a pool thread; puts back whatever a servlet thread had.
            if (previous.authentication == null) SecurityContextHolder.clearContext()
            else SecurityContextHolder.setContext(previous)
        }
    }
}
