package com.homebudget.app.importer.homemoney

import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.BankConnection
import com.homebudget.app.domain.BankConnectionStatus
import com.homebudget.app.domain.User
import com.homebudget.app.repository.BankConnectionRepository
import com.homebudget.app.repository.UserRepository
import com.homebudget.app.security.SecurityUtils
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.util.Date
import javax.crypto.SecretKey

/** The value stored in `bank_connections.provider` for a HomeMoney link. */
const val HOMEMONEY_PROVIDER = "HOMEMONEY"

/**
 * The HomeMoney OAuth link, and the tokens it produces.
 *
 * Stored in `bank_connections` rather than a table of its own. It is the same kind of
 * secret solving the same problem - a third-party credential that has to be kept
 * retrievably and never reach a device - and that table already has the encryption
 * (`SecretCrypto`), the status model and the ownership. What it does not have is a
 * [com.homebudget.app.bank.BankProvider] for HomeMoney, which is why
 * `BankConnectionService.findAll` filters these rows out of the bank screen.
 */
@Service
@Transactional
class HomeMoneyConnectionService(
    private val api: HomeMoneyApi,
    private val connectionRepo: BankConnectionRepository,
    private val userRepo: UserRepository,
    private val securityUtils: SecurityUtils,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    @Value("\${app.homemoney.base-url:https://homemoney.ua}") private val homeMoneyBaseUrl: String,
    @Value("\${app.homemoney.client-id:5}") private val clientId: String,
    @Value("\${app.homemoney.client-secret:demo}") private val clientSecret: String,
    @Value("\${app.base-url:http://localhost:8085}") private val appBaseUrl: String,
    @Value("\${app.jwt.secret:defaultSecretKeyWithAtLeast32CharactersLongForHS256}") private val jwtSecret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * A key of its own, derived from the JWT secret with a domain-separation label.
     *
     * Deliberately not the JWT key. The state token names a user and is handed to a third
     * party, which goes through the browser and comes back on a `permitAll` endpoint - if
     * it were signed with the same key, anyone holding one could present it as a bearer
     * token and be that user.
     */
    private val stateKey: SecretKey = Keys.hmacShaKeyFor(
        MessageDigest.getInstance("SHA-256").digest("homemoney-oauth-state:$jwtSecret".toByteArray())
    )

    private val redirectUri: String get() = "${appBaseUrl.trimEnd('/')}/api/import/homemoney/callback"

    // ── The link ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun find(): BankConnection? = findFor(securityUtils.getCurrentUser())

    /**
     * The user-explicit form.
     *
     * The import runs on a background thread, where there is no security context to read
     * a user out of, so everything past the controller passes one in.
     */
    @Transactional(readOnly = true)
    fun findFor(user: User): BankConnection? =
        connectionRepo.findAllByUser(user).firstOrNull { it.provider == HOMEMONEY_PROVIDER }

    fun require(): BankConnection = find() ?: throw NoSuchElementException("HomeMoney is not connected")

    fun requireFor(user: User): BankConnection =
        findFor(user) ?: throw NoSuchElementException("HomeMoney is not connected")

    fun disconnect() {
        // The imported accounts and their history stay; only the token goes. Deleting
        // somebody's ledger because they unlinked the source would be indefensible.
        find()?.let { connectionRepo.delete(it) }
    }

    /**
     * Where to send the browser to ask HomeMoney for consent.
     *
     * Returned to the client to navigate to, rather than being a redirect of our own: the
     * page has a bearer token and an XHR can carry it, a top-level navigation cannot.
     */
    fun authorizationUrl(): String {
        val user = securityUtils.getCurrentUser()
        val params = mapOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "scope" to "api",
            "state" to newState(user)
        ).entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return "${homeMoneyBaseUrl.trimEnd('/')}/api/oauth/authorize/?$params"
    }

    /**
     * Completes the consent round trip.
     *
     * Runs without a security context - HomeMoney redirects a browser here and browsers
     * do not carry bearer tokens - so the user comes from the signed state and nowhere
     * else.
     */
    fun completeAuthorization(code: String, state: String): BankConnection {
        val user = userOfState(state)
        val token = api.tokenByAuthorizationCode(clientId, clientSecret, redirectUri, code)
        val access = token.accessToken
            ?: throw HomeMoneyAuthException("HomeMoney returned no access token")
        return store(user, access, token.refreshToken)
    }

    /**
     * The development path, for when no OAuth client has been registered yet.
     *
     * Guarded by a flag that is off by default: the redirect flow exists precisely so
     * this application never sees a HomeMoney password, and a switch that quietly stays
     * on in production would undo that.
     */
    fun connectWithPassword(username: String, password: String, allowed: Boolean): BankConnection {
        require(allowed) { "The password grant is disabled; connect through HomeMoney instead" }
        val token = api.tokenByPassword(clientId, clientSecret, username, password)
        val access = token.accessToken
            ?: throw HomeMoneyAuthException("HomeMoney returned no access token")
        return store(securityUtils.getCurrentUser(), access, token.refreshToken)
    }

    private fun store(user: User, accessToken: String, refreshToken: String?): BankConnection {
        val label = runCatching { api.userInfo(accessToken).userName }.getOrNull()
        val credentials = objectMapper.writeValueAsString(
            buildMap {
                put("access_token", accessToken)
                refreshToken?.let { put("refresh_token", it) }
            }
        )
        val existing = findFor(user)
        val connection = existing?.apply {
            this.credentials = credentials
            this.label = label ?: this.label
            this.status = BankConnectionStatus.ACTIVE
            this.lastVerifiedAt = LocalDateTime.now(clock)
            this.updatedAt = LocalDateTime.now(clock)
        } ?: BankConnection(
            user = user,
            provider = HOMEMONEY_PROVIDER,
            credentials = credentials,
            label = label,
            status = BankConnectionStatus.ACTIVE,
            lastVerifiedAt = LocalDateTime.now(clock)
        )
        return connectionRepo.save(connection)
    }

    // ── Using the token ─────────────────────────────────────────────────────

    /**
     * Runs [block] with a working access token, refreshing once if HomeMoney says the
     * token is stale.
     *
     * The retry lives here rather than at each call site so a long import cannot end up
     * refreshing several times in parallel halfway through, and so a connection that is
     * genuinely dead is marked INVALID exactly once.
     */
    fun <T> withToken(connection: BankConnection, block: (String) -> T): T {
        val credentials = credentialsOf(connection)
        val access = credentials["access_token"]
            ?: throw HomeMoneyAuthException("The HomeMoney connection has no token; reconnect")
        return try {
            block(access)
        } catch (first: HomeMoneyAuthException) {
            val refresh = credentials["refresh_token"]
            if (refresh == null) {
                markInvalid(connection)
                throw first
            }
            log.debug("HomeMoney token refused; refreshing once")
            val refreshed = try {
                api.refreshToken(clientId, clientSecret, refresh)
            } catch (e: HomeMoneyAuthException) {
                markInvalid(connection)
                throw e
            }
            val newAccess = refreshed.accessToken ?: run {
                markInvalid(connection)
                throw first
            }
            connection.credentials = objectMapper.writeValueAsString(
                buildMap {
                    put("access_token", newAccess)
                    (refreshed.refreshToken ?: refresh).let { put("refresh_token", it) }
                }
            )
            connection.status = BankConnectionStatus.ACTIVE
            connection.lastVerifiedAt = LocalDateTime.now(clock)
            connectionRepo.save(connection)
            try {
                block(newAccess)
            } catch (second: HomeMoneyAuthException) {
                markInvalid(connection)
                throw second
            }
        }
    }

    private fun markInvalid(connection: BankConnection) {
        connection.status = BankConnectionStatus.INVALID
        connection.updatedAt = LocalDateTime.now(clock)
        connectionRepo.save(connection)
    }

    @Suppress("UNCHECKED_CAST")
    private fun credentialsOf(connection: BankConnection): Map<String, String> =
        objectMapper.readValue(connection.credentials, Map::class.java) as Map<String, String>

    // ── State ───────────────────────────────────────────────────────────────

    private fun newState(user: User): String = Jwts.builder()
        .subject(user.email)
        .issuedAt(Date.from(Instant.now(clock)))
        .expiration(Date.from(Instant.now(clock).plusSeconds(STATE_TTL_SECONDS)))
        .signWith(stateKey)
        .compact()

    private fun userOfState(state: String): User {
        val email = try {
            Jwts.parser().verifyWith(stateKey).build().parseSignedClaims(state).payload.subject
        } catch (e: Exception) {
            // Includes the expired case: ten minutes is plenty to click "allow", and a
            // stale one is far more likely to be a replay than a slow user.
            throw HomeMoneyAuthException("The HomeMoney authorisation link is no longer valid; start again")
        }
        return userRepo.findByEmail(email).orElseThrow {
            HomeMoneyAuthException("The HomeMoney authorisation link is no longer valid; start again")
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val STATE_TTL_SECONDS = 600L
    }
}
