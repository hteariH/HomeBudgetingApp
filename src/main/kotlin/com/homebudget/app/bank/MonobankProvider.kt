package com.homebudget.app.bank

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.homebudget.app.domain.TransactionType
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private const val MONO_API = "https://api.monobank.ua"

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MonoAccount(
    val id: String = "",
    val currencyCode: Int = 980,
    val balance: Long = 0,
    val creditLimit: Long = 0,
    val type: String = "black",
    val maskedPan: List<String> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MonoClientInfo(
    val clientId: String = "",
    val name: String = "",
    val accounts: List<MonoAccount> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MonoStatement(
    val id: String = "",
    val time: Long = 0,
    val description: String = "",
    val mcc: Int = 0,
    val amount: Long = 0,
    val operationAmount: Long = 0,
    val currencyCode: Int = 980,
    val balance: Long = 0
)

/**
 * Monobank's personal API.
 *
 * Personal rather than corporate: the token is one the customer issues themselves at
 * api.monobank.ua. There is no consent flow to automate here - that exists only in
 * Monobank's corporate API, which requires being approved as a corporate client.
 *
 * Statements are rate limited to roughly one request per minute per token, which is
 * why importing is a deliberate, user-initiated action rather than a poll.
 */
@Component
class MonobankProvider(
    private val objectMapper: ObjectMapper
) : BankProvider {

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    override val id = "MONOBANK"
    override val displayName = "Monobank"
    override val helpUrl = "https://api.monobank.ua"

    override val credentialFields = listOf(
        CredentialField(
            name = "token",
            label = "Personal API token",
            type = FieldType.SECRET,
            placeholder = "u1cx...",
            helpText = "Open api.monobank.ua, confirm in the Monobank app, and copy the token."
        )
    )

    private fun tokenOf(credentials: Map<String, String>): String =
        credentials["token"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BankAuthException("Monobank token is missing")

    override fun verify(credentials: Map<String, String>): ProviderIdentity {
        val info = clientInfo(tokenOf(credentials))
        return ProviderIdentity(
            externalClientId = info.clientId,
            displayName = info.name.ifBlank { "Monobank" }
        )
    }

    override fun listAccounts(credentials: Map<String, String>): List<RemoteAccount> =
        clientInfo(tokenOf(credentials)).accounts.map { account ->
            RemoteAccount(
                externalId = account.id,
                displayName = account.maskedPan.firstOrNull() ?: account.type,
                currencyCode = IsoCurrency.codeOf(account.currencyCode),
                // Monobank reports minor units already; the target account's own scale
                // is applied when the import writes the row.
                balanceMinor = account.balance,
                type = account.type
            )
        }

    override fun fetchStatement(
        credentials: Map<String, String>,
        externalAccountId: String,
        from: LocalDate,
        to: LocalDate
    ): List<RemoteTransaction> {
        val fromEpoch = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val toEpoch = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val body = get("/personal/statement/$externalAccountId/$fromEpoch/$toEpoch", tokenOf(credentials))
        val statements: List<MonoStatement> = objectMapper.readValue(
            body,
            objectMapper.typeFactory.constructCollectionType(List::class.java, MonoStatement::class.java)
        )
        return statements.map { stmt ->
            RemoteTransaction(
                externalId = stmt.id,
                occurredAt = Instant.ofEpochSecond(stmt.time),
                description = stmt.description.ifBlank { "—" },
                amountMinor = Math.abs(stmt.amount),
                currencyCode = IsoCurrency.codeOf(stmt.currencyCode),
                type = if (stmt.amount < 0) TransactionType.EXPENSE else TransactionType.INCOME,
                mcc = stmt.mcc
            )
        }
    }

    private fun clientInfo(token: String): MonoClientInfo =
        objectMapper.readValue(get("/personal/client-info", token), MonoClientInfo::class.java)

    private fun get(path: String, token: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$MONO_API$path"))
            .header("X-Token", token)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        when (response.statusCode()) {
            200 -> return response.body()
            401, 403 -> throw BankAuthException("Monobank rejected the token")
            429 -> throw BankRequestException(
                "Monobank is rate limiting requests - statements allow about one request a minute"
            )
            else -> throw BankRequestException("Monobank error ${response.statusCode()}: ${response.body()}")
        }
    }
}

/**
 * Monobank identifies currencies by ISO 4217 *number*; the rest of the app uses the
 * letter code.
 */
object IsoCurrency {
    private val byNumber = mapOf(
        980 to "UAH", 840 to "USD", 978 to "EUR", 826 to "GBP", 985 to "PLN",
        203 to "CZK", 348 to "HUF", 946 to "RON", 756 to "CHF", 124 to "CAD",
        392 to "JPY", 156 to "CNY", 949 to "TRY", 376 to "ILS", 643 to "RUB"
    )

    fun codeOf(number: Int): String =
        byNumber[number] ?: throw BankRequestException("Unsupported currency code $number")
}
