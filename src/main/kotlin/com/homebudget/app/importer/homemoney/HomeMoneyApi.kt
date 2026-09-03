package com.homebudget.app.importer.homemoney

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * homemoney.ua's public API, as much of it as an import needs.
 *
 * It is an ASP.NET `.asmx` service, but nothing here speaks SOAP: every operation also
 * answers a form-encoded POST and replies with JSON. The published WSDL declares every
 * response type as empty, so the shapes below were read off the operation help pages and
 * confirmed against live responses - the same way the bank contract was captured.
 *
 * Three things about this API drive the whole file and are easy to get wrong:
 *
 * - **Every response is HTTP 200**, including "invalid token". The only signal is
 *   [HomeMoneyError.code], so [call] checks it and nothing else may skip that check.
 * - **The path must not end in a slash.** The official documentation writes
 *   `/api2.asmx/TokenPassword/`, which answers "Web Service method name is not valid".
 * - **CurrrencyId really has three r's** in transaction rows, while the same concept is
 *   spelled CurencyId in the save parameters. Both spellings are theirs.
 */
@Component
class HomeMoneyApi(
    private val objectMapper: ObjectMapper,
    @Value("\${app.homemoney.base-url:https://homemoney.ua}") private val baseUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    // ── Operations ──────────────────────────────────────────────────────────

    fun tokenByAuthorizationCode(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        code: String
    ): HomeMoneyToken = call(
        "TokenAuthorizationCode",
        mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "redirect_uri" to redirectUri,
            "code" to code
        ),
        HomeMoneyToken::class.java
    )

    /**
     * The username-and-password grant.
     *
     * Only reachable behind `app.homemoney.allow-password-grant`, which is off outside
     * development: the redirect flow exists precisely so this application never handles
     * somebody's HomeMoney password.
     */
    fun tokenByPassword(
        clientId: String,
        clientSecret: String,
        username: String,
        password: String
    ): HomeMoneyToken = call(
        "TokenPassword",
        mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "grant_type" to "password",
            "username" to username,
            "password" to password
        ),
        HomeMoneyToken::class.java
    )

    fun refreshToken(clientId: String, clientSecret: String, refreshToken: String): HomeMoneyToken = call(
        "TokenRefresh",
        mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "refresh_token" to refreshToken
        ),
        HomeMoneyToken::class.java
    )

    fun userInfo(token: String): HomeMoneyUserInfo =
        call("UserInfo", mapOf("Token" to token), HomeMoneyUserInfo::class.java)

    fun balanceList(token: String): HomeMoneyBalanceList =
        call("BalanceList", mapOf("Token" to token), HomeMoneyBalanceList::class.java)

    fun categoryList(token: String): HomeMoneyCategoryList =
        call("CategoryList", mapOf("Token" to token), HomeMoneyCategoryList::class.java)

    /**
     * Operations in the range, both ends included.
     *
     * Callers walking a long history chunk it themselves. Overlapping chunks are harmless
     * - dedup catches the repeats - but they cost a request each, so the chunker should
     * not overlap on purpose.
     */
    fun transactionsByPeriod(token: String, from: LocalDate, to: LocalDate): HomeMoneyTransactionList = call(
        "TransactionListByPeriod",
        mapOf(
            "Token" to token,
            "From" to from.atStartOfDay().format(DATE_TIME),
            "To" to to.atTime(23, 59, 59).format(DATE_TIME)
        ),
        HomeMoneyTransactionList::class.java
    )

    // ── Transport ───────────────────────────────────────────────────────────

    private fun <T : HomeMoneyResponse> call(operation: String, params: Map<String, String>, type: Class<T>): T {
        val body = params.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/api2.asmx/$operation"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            throw HomeMoneyRequestException("Could not reach HomeMoney: ${e.message}", e)
        }

        if (response.statusCode() != 200) {
            throw HomeMoneyRequestException("HomeMoney returned HTTP ${response.statusCode()} for $operation")
        }

        return parse(operation, response.body(), type)
    }

    /** Split out so the parsing rules can be tested against captured payloads. */
    fun <T : HomeMoneyResponse> parse(operation: String, body: String, type: Class<T>): T {
        val parsed = try {
            objectMapper.readValue(body, type)
        } catch (e: Exception) {
            // A wrong path answers with HTML, not JSON - a trailing slash being the
            // usual cause. Say that rather than leaking a Jackson message.
            throw HomeMoneyRequestException("HomeMoney sent something that is not JSON for $operation", e)
        }

        val error = parsed.error
        if (error != null && error.code != 0) {
            val message = error.message ?: "error ${error.code}"
            log.debug("HomeMoney {} refused: {} ({})", operation, message, error.code)
            if (error.code in AUTH_ERROR_CODES) {
                throw HomeMoneyAuthException("HomeMoney rejected the authorisation: $message")
            }
            throw HomeMoneyRequestException("HomeMoney refused $operation: $message")
        }
        return parsed
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        /** 6 expired, 7 unknown, 13 refresh window closed, 14 bad refresh token. */
        val AUTH_ERROR_CODES = setOf(6, 7, 13, 14)
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}

/** The credentials were refused; the user has to reconnect. */
class HomeMoneyAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** HomeMoney is reachable but unhappy - outage, bad range, unexpected payload. */
class HomeMoneyRequestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

// ── Response shapes ─────────────────────────────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyError(val code: Int = 0, val message: String? = null)

/** Every payload carries the error object; `code == 0` means it worked. */
interface HomeMoneyResponse {
    val error: HomeMoneyError?
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyToken(
    @JsonProperty("Error") override val error: HomeMoneyError? = null,
    @JsonProperty("access_token") val accessToken: String? = null,
    @JsonProperty("refresh_token") val refreshToken: String? = null,
    @JsonProperty("token_type") val tokenType: String? = null,
    @JsonProperty("expires_in") val expiresIn: String? = null
) : HomeMoneyResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyUserInfo(
    @JsonProperty("Error") override val error: HomeMoneyError? = null,
    @JsonProperty("UserName") val userName: String? = null,
    @JsonProperty("IsPremium") val isPremium: Boolean = false,
    @JsonProperty("IsDemo") val isDemo: Boolean = false
) : HomeMoneyResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyBalanceList(
    @JsonProperty("Error") override val error: HomeMoneyError? = null,
    @JsonProperty("defaultcurrency") val defaultCurrency: String? = null,
    @JsonProperty("name") val defaultCurrencyName: String? = null,
    @JsonProperty("ListGroupInfo") val groups: List<HomeMoneyGroup> = emptyList()
) : HomeMoneyResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyGroup(
    @JsonProperty("id") val id: String = "",
    @JsonProperty("name") val name: String = "",
    @JsonProperty("order") val order: Int = 0,
    @JsonProperty("ListAccountInfo") val accounts: List<HomeMoneyAccount> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyAccount(
    @JsonProperty("id") val id: String = "",
    @JsonProperty("name") val name: String = "",
    @JsonProperty("isDefault") val isDefault: Boolean = false,
    @JsonProperty("display") val display: Boolean = true,
    @JsonProperty("includeInBalance") val includeInBalance: Boolean = true,
    @JsonProperty("ListCurrencyInfo") val currencies: List<HomeMoneyAccountCurrency> = emptyList()
)

/**
 * One currency held on one account, with its own balance.
 *
 * This is where HomeMoney's model and ours part company: a single HomeMoney account can
 * hold several currencies at once, ours is always exactly one. Each of these becomes an
 * account of its own on our side.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyAccountCurrency(
    @JsonProperty("id") val id: String = "",
    /** Their display name for the currency; not guaranteed to be an ISO code. */
    @JsonProperty("shortname") val shortName: String = "",
    @JsonProperty("rate") val rate: BigDecimal = BigDecimal.ONE,
    @JsonProperty("balance") val balance: BigDecimal = BigDecimal.ZERO,
    @JsonProperty("display") val display: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyCategoryList(
    @JsonProperty("Error") override val error: HomeMoneyError? = null,
    @JsonProperty("ListCategory") val categories: List<HomeMoneyCategory> = emptyList()
) : HomeMoneyResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyCategory(
    @JsonProperty("id") val id: String = "",
    /** 1 expense, 2 transfer, 3 income - see [HomeMoneyKind]. */
    @JsonProperty("type") val type: Int = 0,
    @JsonProperty("Name") val name: String = "",
    @JsonProperty("FullName") val fullName: String = "",
    @JsonProperty("isArhive") val archived: Boolean = false,
    @JsonProperty("isPinned") val pinned: Boolean = false,
    /** "0" at the root. */
    @JsonProperty("parentId") val parentId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyTransactionList(
    @JsonProperty("Error") override val error: HomeMoneyError? = null,
    @JsonProperty("ListTransaction") val transactions: List<HomeMoneyTransaction> = emptyList()
) : HomeMoneyResponse

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMoneyTransaction(
    @JsonProperty("TransactionId") val transactionId: String = "",
    @JsonProperty("Date") val date: String? = null,
    @JsonProperty("DateUnix") val dateUnix: Long = 0,
    @JsonProperty("CategoryId") val categoryId: String? = null,
    @JsonProperty("CategoryFullName") val categoryFullName: String? = null,
    @JsonProperty("Description") val description: String? = null,
    /** Scheduled, not yet happened. Not part of a ledger. */
    @JsonProperty("isPlan") val plan: Boolean = false,
    /** 1 expense, 2 transfer, 3 income. */
    @JsonProperty("type") val type: Int = 0,
    @JsonProperty("Total") val total: BigDecimal = BigDecimal.ZERO,
    @JsonProperty("AccountId") val accountId: String = "",
    /** Theirs, three r's. */
    @JsonProperty("CurrrencyId") val currencyId: String = "",
    @JsonProperty("TransTotal") val transTotal: BigDecimal = BigDecimal.ZERO,
    @JsonProperty("TransAccountId") val transAccountId: String? = null,
    @JsonProperty("TransCurrencyId") val transCurrencyId: String? = null,
    /** Stable across devices; this is what dedup keys on. */
    @JsonProperty("GUID") val guid: String = "",
    /**
     * HomeMoney tombstones rather than deletes, so a removed operation keeps coming back
     * from the API. Importing one would resurrect it in a ledger it was deleted from.
     */
    @JsonProperty("isDeleted") val deleted: Boolean = false
) {
    /**
     * When the operation happened.
     *
     * `DateUnix` is preferred because it needs no format guess. Its unit is not
     * documented, so a value past the year 5138 is read as milliseconds rather than
     * silently landing five thousand years out.
     */
    fun occurredAt(): Instant {
        if (dateUnix > 0) {
            return if (dateUnix > MILLIS_THRESHOLD) Instant.ofEpochMilli(dateUnix)
            else Instant.ofEpochSecond(dateUnix)
        }
        val raw = date?.trim().orEmpty()
        if (raw.isEmpty()) {
            throw HomeMoneyRequestException("Operation $guid has neither DateUnix nor Date")
        }
        return parseDate(raw)
    }

    private fun parseDate(raw: String): Instant {
        // "/Date(1700000000000)/" is how ASP.NET's older serialiser writes an instant.
        DOT_NET_DATE.find(raw)?.let { return Instant.ofEpochMilli(it.groupValues[1].toLong()) }
        return runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }
            .recoverCatching { LocalDate.parse(raw).atStartOfDay().toInstant(ZoneOffset.UTC) }
            .getOrElse { throw HomeMoneyRequestException("Cannot read the date '$raw' on operation $guid") }
    }

    private companion object {
        const val MILLIS_THRESHOLD = 100_000_000_000L
        val DOT_NET_DATE = Regex("""/Date\((-?\d+)""")
    }
}

/** HomeMoney's shared type code, used for both operations and categories. */
object HomeMoneyKind {
    const val EXPENSE = 1
    const val TRANSFER = 2
    const val INCOME = 3
}
