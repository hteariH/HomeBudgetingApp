package com.homebudget.app.bank

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Conditional
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * Monobank, connected the way a service is meant to connect to it.
 *
 * The customer never handles a token. They press connect, approve the request in the
 * Monobank app - a QR code on a desktop, a tap on a phone - and the access this app then
 * holds is a request id that Monobank issued to *this app* for *that customer*. They can
 * see it in the app and take it back there, which a personal token does not offer.
 *
 * The price is on this side: the deployment has to be an approved company with a signing
 * key, and the permissions the customer is offered are the ones Monobank approved the
 * company for, not something chosen per request. Without a key configured this bean does
 * not exist, and the bank list falls back to [MonobankPersonalProvider].
 *
 * @see MonobankCorporateApi for the signing scheme.
 */
@Component
@Conditional(MonobankCorporateConfigured::class)
class MonobankCorporateProvider(
    private val properties: MonobankCorporateProperties,
    objectMapper: ObjectMapper,
    private val clock: Clock
) : ConsentProvider {

    private val api = MonobankCorporateApi(properties, objectMapper, clock)

    override val id = "MONOBANK_CORPORATE"
    override val displayName = "Monobank"
    override val helpUrl = null

    /** Nothing to type: the whole point of this provider is that there is no token. */
    override val credentialFields = emptyList<CredentialField>()

    override fun beginConsent(): ConsentChallenge {
        val request = api.requestAccess()
        return ConsentChallenge(
            reference = request.tokenRequestId,
            approveUrl = request.acceptUrl,
            expiresAt = clock.instant().plus(properties.consentTimeout)
        )
    }

    override fun completeConsent(reference: String): Map<String, String>? =
        if (api.isAccessGranted(reference)) mapOf(REQUEST_ID to reference) else null

    override fun verify(credentials: Map<String, String>): ProviderIdentity {
        val info = api.clientInfo(requestIdOf(credentials))
        return ProviderIdentity(
            externalClientId = info.clientId,
            // The name is only there when the company was approved for personal data;
            // with statement-only permissions the account still works, unlabelled.
            displayName = info.name.ifBlank { "Monobank" }
        )
    }

    override fun listAccounts(credentials: Map<String, String>): List<RemoteAccount> =
        api.clientInfo(requestIdOf(credentials)).accounts.map { it.toRemoteAccount() }

    override fun fetchStatement(
        credentials: Map<String, String>,
        externalAccountId: String,
        from: LocalDate,
        to: LocalDate
    ): List<RemoteTransaction> {
        val (fromEpoch, toEpoch) = monoStatementWindow(from, to)
        return api.statement(requestIdOf(credentials), externalAccountId, fromEpoch, toEpoch)
            .map { it.toRemoteTransaction() }
    }

    private fun requestIdOf(credentials: Map<String, String>): String =
        credentials[REQUEST_ID]?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw BankAuthException("This Monobank connection has no access request - reconnect")

    private companion object {
        /**
         * The credential key. Named for what Monobank calls it in the header it becomes,
         * because that is where anyone debugging this will meet it.
         */
        const val REQUEST_ID = "requestId"
    }
}
