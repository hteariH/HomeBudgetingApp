package com.homebudget.app.token

import com.homebudget.app.domain.User
import java.time.Instant

/** What a token list shows: a personal token, or (later) an OAuth grant. */
enum class TokenKind { PERSONAL, OAUTH }

/**
 * One row of Settings → Assistants. [id] is what `DELETE /api/assistants/tokens/{id}`
 * takes. Never carries the token itself.
 */
data class TokenView(
    val id: String,
    val kind: TokenKind,
    val name: String,
    val prefix: String?,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val expiresAt: Instant?
)

/** The one response that carries the raw token. */
data class CreatedToken(val token: String, val item: TokenView)

data class CreateTokenRequest(val name: String? = null, val expiresInDays: Int? = null)

/** Whom a token presented on `/mcp` belongs to. */
data class AuthenticatedToken(val user: User, val tokenId: Long)
