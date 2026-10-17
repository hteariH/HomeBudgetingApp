package com.homebudget.app.oauth

import com.homebudget.app.domain.User
import com.homebudget.app.mcp.McpSettings
import com.homebudget.app.token.AccessToken
import com.homebudget.app.token.AccessTokenKind
import com.homebudget.app.token.AccessTokenRepository
import com.homebudget.app.token.AccessTokenService
import com.homebudget.app.token.newToken
import com.homebudget.app.token.sha256Hex
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** An RFC 6749 error, answered as `{ error, error_description }`. */
class OAuthException(val error: String, description: String) : RuntimeException(description)

/** A request whose client or redirect URI cannot be trusted: shown as a page, never redirected. */
class UntrustedRedirectException(message: String) : RuntimeException(message)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val scope: String
)

/** What `GET /oauth/authorize` and `POST /api/oauth/authorize` both check. */
data class AuthorizationRequest(
    val clientId: String?,
    val redirectUri: String?,
    val responseType: String?,
    val codeChallenge: String?,
    val codeChallengeMethod: String?,
    val scope: String?
)

/**
 * The authorization server: registration, the code + PKCE flow, refresh rotation and
 * revocation. Codes and tokens exist in clear only in the responses that hand them out.
 *
 * Errors that revoke a grant (a reused code or refresh token) are [OAuthException]s that do
 * not roll the transaction back, so the revocation sticks while the caller gets
 * `invalid_grant`.
 */
@Service
@Transactional(noRollbackFor = [OAuthException::class])
class OAuthService(
    private val clients: OAuthClientRepository,
    private val grants: OAuthGrantRepository,
    private val codes: OAuthCodeRepository,
    private val tokenRepo: AccessTokenRepository,
    private val tokens: AccessTokenService,
    private val mcp: McpSettings,
    private val clock: Clock,
    @Value("\${app.base-url:}") baseUrl: String
) {
    companion object {
        const val SCOPE = "ledger:write"
        const val ACCESS_PREFIX = "pma_"
        const val REFRESH_PREFIX = "pmr_"
        val ACCESS_LIFETIME: Duration = Duration.ofHours(1)
        val REFRESH_LIFETIME: Duration = Duration.ofDays(90)
        val CODE_LIFETIME: Duration = Duration.ofSeconds(60)
        const val DEFAULT_CLIENT_NAME = "Unnamed assistant"
        const val MAX_CLIENT_NAME = 200
        const val MAX_REDIRECT_URIS = 10
        const val MAX_REDIRECT_URI_LENGTH = 1000
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")
        /** RFC 7636 §4.1: the verifier's alphabet and length; a challenge is drawn from the same. */
        private val PKCE_VALUE = Regex("[A-Za-z0-9._~-]{43,128}")
        private val random = SecureRandom()
    }

    /** `APP_BASE_URL` without a trailing slash. */
    val base: String = baseUrl.trim().trimEnd('/')

    /** OAuth exists only while MCP is on and the instance knows its own address. */
    fun available(): Boolean = base.isNotEmpty() && mcp.enabled()

    // ---- registration -------------------------------------------------------------------

    /** Registers a public client; [OAuthException] with `invalid_redirect_uri` or `invalid_client_metadata`. */
    fun register(redirectUris: List<String>?, clientName: String?): OAuthClient {
        if (redirectUris.isNullOrEmpty() || redirectUris.size > MAX_REDIRECT_URIS) {
            throw OAuthException("invalid_redirect_uri", "Give 1 to $MAX_REDIRECT_URIS redirect_uris")
        }
        redirectUris.forEach(::checkRedirectUri)
        val name = clientName?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CLIENT_NAME
        if (name.length > MAX_CLIENT_NAME) {
            throw OAuthException("invalid_client_metadata", "client_name is longer than $MAX_CLIENT_NAME characters")
        }
        return clients.save(
            OAuthClient(
                clientId = newToken(""),
                clientName = name,
                redirectUris = redirectUris.distinct().joinToString("\n"),
                createdAt = Instant.now(clock)
            )
        )
    }

    /** https anywhere, or plain http on the loopback interface; never a fragment. */
    private fun checkRedirectUri(raw: String) {
        fun bad(why: String): Nothing = throw OAuthException("invalid_redirect_uri", why)
        if (raw.length > MAX_REDIRECT_URI_LENGTH) bad("A redirect URI is longer than $MAX_REDIRECT_URI_LENGTH characters")
        val uri = try {
            URI(raw)
        } catch (e: URISyntaxException) {
            bad("A redirect URI is not a valid URI")
        }
        if (uri.rawFragment != null || raw.contains('#')) bad("A redirect URI must not have a fragment")
        if (uri.rawUserInfo != null) bad("A redirect URI must not carry user information")
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty()) bad("A redirect URI needs a host")
        when (uri.scheme?.lowercase()) {
            "https" -> Unit
            "http" -> if (host !in LOOPBACK_HOSTS) bad("Plain http is allowed only for localhost and 127.0.0.1")
            else -> bad("A redirect URI must use https")
        }
    }

    // ---- authorization ------------------------------------------------------------------

    /**
     * Checks an authorization request. A client or redirect URI that does not check out is an
     * [UntrustedRedirectException]; anything else wrong is an [OAuthException] (`invalid_request`)
     * that may be sent back to the now-verified redirect URI.
     */
    @Transactional(readOnly = true)
    fun check(request: AuthorizationRequest): OAuthClient {
        val client = request.clientId?.let(clients::findByClientId)
            ?: throw UntrustedRedirectException("This assistant is not registered with this server.")
        if (!client.allowsRedirect(request.redirectUri)) {
            throw UntrustedRedirectException("The address this assistant asked to return to is not one it registered.")
        }
        fun invalid(why: String): Nothing = throw OAuthException("invalid_request", why)
        if (request.responseType != "code") invalid("response_type must be code")
        if (request.codeChallengeMethod != "S256") invalid("PKCE with code_challenge_method S256 is required")
        if (request.codeChallenge == null || !PKCE_VALUE.matches(request.codeChallenge)) invalid("code_challenge is missing or malformed")
        val scopes = request.scope?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
        if (scopes.any { it != SCOPE }) invalid("The only scope is $SCOPE")
        return client
    }

    /** What the consent page shows about a client. */
    @Transactional(readOnly = true)
    fun client(clientId: String): OAuthClient =
        clients.findByClientId(clientId) ?: throw NoSuchElementException("Assistant not found")

    /**
     * The user's answer on the consent page, as the URL to send the browser to. An approval
     * makes a grant and a code; a refusal sends `access_denied`.
     */
    fun decide(user: User, request: AuthorizationRequest, state: String?, approve: Boolean): String {
        val client = try {
            check(request)
        } catch (e: UntrustedRedirectException) {
            throw IllegalArgumentException(e.message)
        }
        val redirectUri = request.redirectUri!!
        if (!approve) return withQuery(redirectUri, "error" to "access_denied", "state" to state)
        val now = Instant.now(clock)
        val grant = grants.save(OAuthGrant(client = client, user = user, scope = SCOPE, createdAt = now))
        val bytes = ByteArray(32).also(random::nextBytes)
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        codes.save(
            OAuthCode(
                codeHash = sha256Hex(code),
                grant = grant,
                redirectUri = redirectUri,
                codeChallenge = request.codeChallenge!!,
                expiresAt = now.plus(CODE_LIFETIME)
            )
        )
        return withQuery(redirectUri, "code" to code, "state" to state)
    }

    /** [uri] with [params] (null values left out) appended after `?` or `&`. */
    fun withQuery(uri: String, vararg params: Pair<String, String?>): String {
        val query = params.filter { it.second != null }
            .joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
        if (query.isEmpty()) return uri
        val separator = when {
            !uri.contains('?') -> "?"
            uri.endsWith("?") || uri.endsWith("&") -> ""
            else -> "&"
        }
        return uri + separator + query
    }

    // ---- tokens -------------------------------------------------------------------------

    fun exchangeCode(code: String?, redirectUri: String?, clientId: String?, verifier: String?): TokenResponse {
        if (code.isNullOrEmpty() || redirectUri.isNullOrEmpty() || clientId.isNullOrEmpty() || verifier.isNullOrEmpty()) {
            throw OAuthException("invalid_request", "code, redirect_uri, client_id and code_verifier are required")
        }
        val client = clients.findByClientId(clientId) ?: throw OAuthException("invalid_client", "Unknown client")
        val stored = codes.findByCodeHash(sha256Hex(code)) ?: throw invalidGrant()
        val now = Instant.now(clock)
        // Marked before anything else is checked, so a second presentation is caught even when
        // two arrive at once: whoever finds the code already used revokes what it bought.
        if (stored.usedAt != null || codes.markUsed(stored.id, now) == 0) {
            tokens.revokeGrant(stored.grant)
            throw invalidGrant()
        }
        val grant = stored.grant
        if (grant.client.id != client.id) throw invalidGrant()
        if (!stored.expiresAt.isAfter(now)) throw invalidGrant()
        if (grant.revokedAt != null) throw invalidGrant()
        if (stored.redirectUri != redirectUri) throw invalidGrant()
        if (!PKCE_VALUE.matches(verifier) || !challengeMatches(verifier, stored.codeChallenge)) throw invalidGrant()
        client.lastUsedAt = now
        return issue(grant, now)
    }

    fun refresh(refreshToken: String?, clientId: String?): TokenResponse {
        if (refreshToken.isNullOrEmpty() || clientId.isNullOrEmpty()) {
            throw OAuthException("invalid_request", "refresh_token and client_id are required")
        }
        val client = clients.findByClientId(clientId) ?: throw OAuthException("invalid_client", "Unknown client")
        val stored = tokenRepo.findByTokenHash(sha256Hex(refreshToken))
            ?.takeIf { it.kind == AccessTokenKind.OAUTH_REFRESH && it.oauthGrantId != null }
            ?: throw invalidGrant()
        val grant = grants.findById(stored.oauthGrantId!!).orElseThrow { invalidGrant() }
        if (grant.client.id != client.id) throw invalidGrant()
        val now = Instant.now(clock)
        if (stored.usedAt != null || tokenRepo.markUsed(stored.id, now) == 0) {
            // A rotated refresh token came back: someone holds a copy. Nothing it bought stands.
            tokens.revokeGrant(grant)
            throw invalidGrant()
        }
        if (stored.expiresAt?.isAfter(now) != true) throw invalidGrant()
        if (grant.revokedAt != null) throw invalidGrant()
        client.lastUsedAt = now
        return issue(grant, now)
    }

    /** RFC 7009: revokes the grant behind an access or refresh token; anything else is ignored. */
    fun revoke(raw: String?) {
        if (raw.isNullOrEmpty()) return
        val token = tokenRepo.findByTokenHash(sha256Hex(raw)) ?: return
        val grantId = token.oauthGrantId ?: return
        grants.findById(grantId).ifPresent(tokens::revokeGrant)
    }

    private fun issue(grant: OAuthGrant, now: Instant): TokenResponse {
        val access = newToken(ACCESS_PREFIX)
        val refresh = newToken(REFRESH_PREFIX)
        fun save(raw: String, kind: AccessTokenKind, lifetime: Duration) = tokenRepo.save(
            AccessToken(
                user = grant.user,
                kind = kind,
                name = grant.client.clientName.take(AccessTokenService.MAX_NAME_LENGTH),
                tokenHash = sha256Hex(raw),
                prefix = raw.take(AccessTokenService.SHOWN_PREFIX_LENGTH),
                oauthGrantId = grant.id,
                createdAt = now,
                expiresAt = now.plus(lifetime)
            )
        )
        save(access, AccessTokenKind.OAUTH_ACCESS, ACCESS_LIFETIME)
        save(refresh, AccessTokenKind.OAUTH_REFRESH, REFRESH_LIFETIME)
        return TokenResponse(access, refresh, ACCESS_LIFETIME.seconds, grant.scope)
    }

    private fun challengeMatches(verifier: String, challenge: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return MessageDigest.isEqual(computed.toByteArray(Charsets.US_ASCII), challenge.toByteArray(Charsets.US_ASCII))
    }

    private fun invalidGrant() = OAuthException("invalid_grant", "The code or token is invalid, expired or already used")
}
