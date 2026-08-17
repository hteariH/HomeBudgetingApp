package com.homebudget.app.bank

import com.homebudget.app.domain.TransactionType
import java.time.Instant
import java.time.LocalDate

/**
 * A bank the app can import from.
 *
 * The point of this interface is that adding a bank should cost one implementation and
 * nothing else - no new endpoint, no new screen, no schema change. Two decisions carry
 * that weight:
 *
 * - **Credentials are opaque.** [credentialFields] *describes* what the bank needs and
 *   the value travels as a map, so a bank wanting a client id and a secret fits the
 *   same table and the same form as one wanting a single token.
 * - **The form is data, not code.** The web client renders [credentialFields] rather
 *   than shipping a component per bank, which is what stops every new integration from
 *   turning into another bespoke page.
 */
interface BankProvider {

    /** Stable id stored in `bank_connections.provider`. */
    val id: String

    /** Shown in the bank picker. */
    val displayName: String

    /** Where the user gets the credentials, linked from the form. */
    val helpUrl: String?

    val credentialFields: List<CredentialField>

    /**
     * Checks the credentials and reports who they belong to.
     *
     * Called before anything is stored, so a mistyped token fails in front of the user
     * instead of becoming a connection that silently never works.
     *
     * @throws BankAuthException when the bank rejects the credentials.
     */
    fun verify(credentials: Map<String, String>): ProviderIdentity

    /** The accounts these credentials can see, for linking to local ones. */
    fun listAccounts(credentials: Map<String, String>): List<RemoteAccount>

    fun fetchStatement(
        credentials: Map<String, String>,
        externalAccountId: String,
        from: LocalDate,
        to: LocalDate
    ): List<RemoteTransaction>
}

/** One input in the connect form. */
data class CredentialField(
    val name: String,
    val label: String,
    val type: FieldType = FieldType.SECRET,
    val placeholder: String? = null,
    val helpText: String? = null,
    val required: Boolean = true
)

enum class FieldType {
    /** Masked in the UI and never echoed back by the API. */
    SECRET,
    TEXT
}

/** Who the credentials belong to, used to label the connection. */
data class ProviderIdentity(
    val externalClientId: String,
    val displayName: String
)

data class RemoteAccount(
    val externalId: String,
    val displayName: String,
    val currencyCode: String,
    /** Minor units, in the account's own currency. */
    val balanceMinor: Long,
    val type: String? = null
)

data class RemoteTransaction(
    val externalId: String,
    val occurredAt: Instant,
    val description: String,
    /** Positive magnitude; direction is in [type]. */
    val amountMinor: Long,
    val currencyCode: String,
    val type: TransactionType,
    /** Merchant category code, 0 when the bank does not supply one. */
    val mcc: Int = 0
)

/** The bank refused the credentials - a user-fixable problem, not a server fault. */
class BankAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The bank is reachable but unhappy (rate limit, outage, bad range). */
class BankRequestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
