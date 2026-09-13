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
     * How the user proves the account is theirs.
     *
     * The client renders one of two things off this, and nothing bank-specific either
     * way: a form built from [credentialFields], or the approve-on-your-phone dialog a
     * [ConsentProvider] drives.
     */
    val authMode: AuthMode get() = AuthMode.CREDENTIALS

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

enum class AuthMode {
    /** The user types what the bank gave them - see [BankProvider.credentialFields]. */
    CREDENTIALS,

    /** The user approves the request inside the bank's own app - see [ConsentProvider]. */
    CONSENT
}

/**
 * A bank that hands out access itself, rather than expecting the user to arrive with a
 * credential already in hand.
 *
 * The distinction is worth an interface because the two are not the same shape. A form is
 * one round trip: the user types, the bank answers. Consent is three parties and an
 * unknown wait - the app asks the bank for a request, the *user* approves it somewhere
 * else entirely, and only then is there anything to store. Nothing about that fits in
 * [BankProvider.verify], which is handed credentials that already exist.
 *
 * What comes back from [completeConsent] is an ordinary credential map, so everything past
 * this point - encryption, [BankProvider.verify], statements - is the same code the form
 * providers use.
 */
interface ConsentProvider : BankProvider {

    override val authMode: AuthMode get() = AuthMode.CONSENT

    /** Asks the bank for something the user can approve. */
    fun beginConsent(): ConsentChallenge

    /**
     * The credentials [reference] earned, or null while the user has not approved it yet.
     *
     * Null is the expected answer for as long as the user is still looking at their phone,
     * which is why it is a return value rather than an exception: the caller polls this.
     */
    fun completeConsent(reference: String): Map<String, String>?
}

/**
 * Something for the user to approve, and the handle to ask about it afterwards.
 *
 * [approveUrl] is the bank's, not ours: on a phone it opens the bank's app, and anywhere
 * else it has to become a QR code for one.
 */
data class ConsentChallenge(
    val reference: String,
    val approveUrl: String,
    val expiresAt: Instant
)

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
