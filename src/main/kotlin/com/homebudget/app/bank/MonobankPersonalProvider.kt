package com.homebudget.app.bank

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate

/**
 * Monobank's personal API.
 *
 * The token is one the customer issues themselves at api.monobank.ua, which is a real
 * obstacle: it is a page most people have never heard of, and a token that reads every
 * transaction they have ever made is a lot to ask somebody to paste into a form. That is
 * what [MonobankCorporateProvider] exists to avoid, and why it is the one offered first.
 *
 * Kept because it needs nothing from the bank: no approved company, no key, no
 * registration. A self-hosted copy of this app works with it on day one, and connections
 * already made with it keep working.
 *
 * Statements are rate limited to roughly one request per minute per token, which is why
 * importing is a deliberate, user-initiated action rather than a poll.
 */
@Component
class MonobankPersonalProvider(
    private val objectMapper: ObjectMapper
) : BankProvider {

    private val httpClient: HttpClient = HttpClient.newHttpClient()

    /** Unchanged from when this was the only Monobank: it is stored in existing rows. */
    override val id = "MONOBANK"
    override val displayName = "Monobank (personal token)"
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
        clientInfo(tokenOf(credentials)).accounts.map { it.toRemoteAccount() }

    override fun fetchStatement(
        credentials: Map<String, String>,
        externalAccountId: String,
        from: LocalDate,
        to: LocalDate
    ): List<RemoteTransaction> {
        val (fromEpoch, toEpoch) = monoStatementWindow(from, to)
        val body = get("/personal/statement/$externalAccountId/$fromEpoch/$toEpoch", tokenOf(credentials))
        val statements: List<MonoStatement> = objectMapper.readValue(
            body,
            objectMapper.typeFactory.constructCollectionType(List::class.java, MonoStatement::class.java)
        )
        return statements.map { it.toRemoteTransaction() }
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
