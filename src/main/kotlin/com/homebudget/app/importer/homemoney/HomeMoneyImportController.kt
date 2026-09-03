package com.homebudget.app.importer.homemoney

import com.homebudget.app.domain.CategoryType
import com.homebudget.app.security.SecurityUtils
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime

// ── Wire types ──────────────────────────────────────────────────────────────

data class HomeMoneyConnectionResponse(
    val connected: Boolean,
    /** The HomeMoney account name, so the user can see whose data this is. */
    val label: String? = null,
    val status: String? = null,
    val lastVerifiedAt: LocalDateTime? = null
)

data class HomeMoneyAuthorizationResponse(val authorizationUrl: String)

/** Only reachable while `app.homemoney.allow-password-grant` is on; see the controller. */
data class HomeMoneyPasswordRequest(
    @field:NotBlank val username: String = "",
    @field:NotBlank val password: String = ""
)

/**
 * Defaults on every optional field, and `included` defaults to **true**.
 *
 * Not decoration: a client whose serialiser omits values equal to their own defaults would
 * otherwise have an unchecked account and an untouched one arrive identically, and the
 * safe reading of "the field is missing" is the one the user saw on screen - included.
 */
data class PlannedAccountDto(
    val externalId: String,
    val name: String,
    val currencyLabel: String = "",
    val currencyCode: String? = null,
    /** Decimal, like every other amount on this API - never a floating-point number. */
    val balance: String = "0",
    val targetAccountId: Long? = null,
    val included: Boolean = true
)

data class PlannedCategoryDto(
    val externalId: String,
    val name: String,
    val fullName: String = "",
    val type: CategoryType,
    val parentExternalId: String? = null,
    val archived: Boolean = false,
    val targetCategoryId: Long? = null
)

data class HomeMoneyPlanResponse(
    val accounts: List<PlannedAccountDto>,
    val categories: List<PlannedCategoryDto>,
    val transactionCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val newAccountCount: Int,
    val newCategoryCount: Int,
    /** Non-null means the import cannot run as proposed, and says why in plain words. */
    val blockedReason: String?
)

/**
 * The plan as the user left it.
 *
 * Sent back whole rather than as a diff: the mapping is what the user agreed to, and a
 * patch against a plan the server would have to recompute is a chance for the two to
 * disagree about what was on screen.
 */
data class HomeMoneyApplyRequest(
    val accounts: List<PlannedAccountDto> = emptyList(),
    val categories: List<PlannedCategoryDto> = emptyList()
)

data class HomeMoneyStatusResponse(
    val state: String,
    val accountsCreated: Int,
    val categoriesCreated: Int,
    val imported: Int,
    val skipped: Int,
    val processedChunks: Int,
    val totalChunks: Int,
    val error: String?
)

/**
 * Import from homemoney.ua.
 *
 * Its own controller rather than a provider under `/api/bank`, because it is a different
 * operation: a bank import adds a date range of one account's statement to a ledger that
 * already exists, this builds the ledger - accounts, category tree, every operation, and
 * the balances they have to add up to.
 *
 * The OAuth round trip is split in an unusual way and deliberately so. [authorize] is a
 * normal authenticated call that hands back a URL, because a top-level browser navigation
 * cannot carry a bearer token; [callback] is open, because HomeMoney redirects a browser
 * to it, and takes the user's identity from the signed `state` it minted in [authorize].
 */
@RestController
@RequestMapping("/api/import/homemoney")
class HomeMoneyImportController(
    private val connections: HomeMoneyConnectionService,
    private val service: HomeMoneyImportService,
    private val runner: HomeMoneyImportRunner,
    private val statuses: HomeMoneyImportStatuses,
    private val securityUtils: SecurityUtils,
    @Value("\${app.base-url:http://localhost:8085}") private val appBaseUrl: String,
    @Value("\${app.homemoney.allow-password-grant:false}") private val allowPasswordGrant: Boolean
) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    // ── Connection ──────────────────────────────────────────────────────────

    @GetMapping("/connection")
    fun connection(): HomeMoneyConnectionResponse {
        val connection = connections.find()
            ?: return HomeMoneyConnectionResponse(connected = false)
        return HomeMoneyConnectionResponse(
            connected = true,
            label = connection.label,
            status = connection.status.name,
            lastVerifiedAt = connection.lastVerifiedAt
        )
    }

    @PostMapping("/authorize")
    fun authorize(): HomeMoneyAuthorizationResponse =
        HomeMoneyAuthorizationResponse(connections.authorizationUrl())

    /**
     * Where HomeMoney sends the browser back to.
     *
     * Answers with a redirect into the app rather than JSON: whatever is on the other end
     * of this is a browser window the user is looking at, and a page of JSON is not an
     * answer to "did that work". The outcome travels as a query parameter the import
     * screen reads.
     */
    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?
    ): ResponseEntity<Void> {
        val outcome = when {
            error != null -> "denied"
            code.isNullOrBlank() || state.isNullOrBlank() -> "denied"
            else -> try {
                connections.completeAuthorization(code, state)
                "connected"
            } catch (e: Exception) {
                // Every failure, not just the two HomeMoney ones. Whatever is on the
                // other end of this is a browser window the user is watching, and the
                // exception handler's JSON 500 would land in it as a page of text with
                // no way back into the app.
                log.warn("HomeMoney authorisation could not be completed", e)
                "failed"
            }
        }
        val target = UriComponentsBuilder
            .fromUriString("${appBaseUrl.trimEnd('/')}/app/")
            .queryParam("homemoney", outcome)
            .build()
            .toUriString()
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build()
    }

    /**
     * Connect with a HomeMoney username and password.
     *
     * Off unless `app.homemoney.allow-password-grant` says otherwise, which it does not in
     * production. It exists for development against an unregistered OAuth client; the
     * redirect flow is the real one precisely because it keeps somebody's HomeMoney
     * password out of this application entirely.
     */
    @PostMapping("/connection/password")
    fun connectWithPassword(@RequestBody req: HomeMoneyPasswordRequest): HomeMoneyConnectionResponse {
        val connection = connections.connectWithPassword(req.username, req.password, allowPasswordGrant)
        return HomeMoneyConnectionResponse(
            connected = true,
            label = connection.label,
            status = connection.status.name,
            lastVerifiedAt = connection.lastVerifiedAt
        )
    }

    @DeleteMapping("/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disconnect() = connections.disconnect()

    // ── Import ──────────────────────────────────────────────────────────────

    @PostMapping("/plan")
    fun plan(): HomeMoneyPlanResponse =
        service.plan(securityUtils.getCurrentUser().id).toResponse()

    /**
     * Starts the import and returns immediately.
     *
     * 202 rather than 200: a decade of history is minutes of work, and the client watches
     * `/status` rather than holding a request open for it.
     */
    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun apply(@RequestBody req: HomeMoneyApplyRequest): HomeMoneyStatusResponse {
        val userId = securityUtils.getCurrentUser().id
        require(statuses.claim(userId)) { "An import is already running" }
        runner.run(userId, req.toPlan())
        return statuses.of(userId).toResponse()
    }

    @GetMapping("/status")
    fun status(): HomeMoneyStatusResponse =
        statuses.of(securityUtils.getCurrentUser().id).toResponse()

    /** HomeMoney refused the token. The user has to reconnect, so this is not a 500. */
    @org.springframework.web.bind.annotation.ExceptionHandler(HomeMoneyAuthException::class)
    fun handleAuth(ex: HomeMoneyAuthException): ResponseEntity<com.homebudget.app.controller.ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            com.homebudget.app.controller.ErrorResponse(
                status = 422, error = "HomeMoney Rejected Authorisation", message = ex.message
            )
        )

    /** HomeMoney is reachable but unhappy - an outage of theirs, not a fault of ours. */
    @org.springframework.web.bind.annotation.ExceptionHandler(HomeMoneyRequestException::class)
    fun handleRequest(ex: HomeMoneyRequestException): ResponseEntity<com.homebudget.app.controller.ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            com.homebudget.app.controller.ErrorResponse(
                status = 502, error = "HomeMoney Unavailable", message = ex.message
            )
        )
}

// ── Mapping ─────────────────────────────────────────────────────────────────

private fun HomeMoneyPlan.toResponse() = HomeMoneyPlanResponse(
    accounts = accounts.map {
        PlannedAccountDto(
            externalId = it.externalId,
            name = it.name,
            currencyLabel = it.currencyLabel,
            currencyCode = it.currencyCode,
            balance = it.balance.toPlainString(),
            targetAccountId = it.targetAccountId,
            included = it.included
        )
    },
    categories = categories.map {
        PlannedCategoryDto(
            externalId = it.externalId,
            name = it.name,
            fullName = it.fullName,
            type = it.type,
            parentExternalId = it.parentExternalId,
            archived = it.archived,
            targetCategoryId = it.targetCategoryId
        )
    },
    transactionCount = transactionCount,
    firstDate = firstDate,
    lastDate = lastDate,
    newAccountCount = newAccountCount,
    newCategoryCount = newCategoryCount,
    blockedReason = blockedReason
)

/**
 * Back into the plan the importer works with.
 *
 * `blockedReason` is deliberately not carried across: it is the server's own verdict, and
 * a client that simply omitted it would otherwise talk its way past the account cap. The
 * import service recomputes it before creating anything.
 */
private fun HomeMoneyApplyRequest.toPlan() = HomeMoneyPlan(
    accounts = accounts.map {
        val parts = it.externalId.split(':')
        PlannedAccount(
            externalId = it.externalId,
            homeMoneyAccountId = parts.getOrElse(1) { "" },
            homeMoneyCurrencyId = parts.getOrElse(2) { "" },
            name = it.name,
            currencyLabel = it.currencyLabel,
            currencyCode = it.currencyCode,
            balance = it.balance.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            targetAccountId = it.targetAccountId,
            included = it.included
        )
    },
    categories = categories.map {
        PlannedCategory(
            externalId = it.externalId,
            name = it.name,
            fullName = it.fullName,
            type = it.type,
            parentExternalId = it.parentExternalId,
            archived = it.archived,
            targetCategoryId = it.targetCategoryId
        )
    }
)

private fun HomeMoneyImportStatus.toResponse() = HomeMoneyStatusResponse(
    state = state.name,
    accountsCreated = accountsCreated,
    categoriesCreated = categoriesCreated,
    imported = imported,
    skipped = skipped,
    processedChunks = processedChunks,
    totalChunks = totalChunks,
    error = error
)
