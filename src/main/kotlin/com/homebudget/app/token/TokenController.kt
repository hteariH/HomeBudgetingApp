package com.homebudget.app.token

import com.homebudget.app.mcp.McpSettings
import com.homebudget.app.security.SecurityUtils
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * Settings → Assistants: the signed-in user's personal tokens and OAuth grants. While MCP is
 * off no new token can be made, but existing ones can still be listed and revoked, so a user
 * can cut an assistant off during an incident.
 */
@RestController
@RequestMapping("/api/assistants/tokens")
class TokenController(
    private val tokens: AccessTokenService,
    private val mcp: McpSettings,
    private val securityUtils: SecurityUtils
) {
    @GetMapping
    fun list(): List<TokenView> = tokens.listFor(securityUtils.getCurrentUser())

    /** The only response that ever carries the token itself. */
    @PostMapping
    fun create(@RequestBody req: CreateTokenRequest): CreatedToken {
        requireEnabled()
        return tokens.createPersonal(req.name ?: "", req.expiresInDays)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@PathVariable id: String) = tokens.revoke(id)

    private fun requireEnabled() {
        if (!mcp.enabled()) throw NoSuchElementException("Not found")
    }
}
