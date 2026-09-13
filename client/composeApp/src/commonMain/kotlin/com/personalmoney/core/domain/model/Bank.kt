package com.personalmoney.core.domain.model

import com.personalmoney.core.domain.TransactionType
import kotlinx.datetime.LocalDate

/**
 * Bank connections, as the rest of the app thinks about them.
 *
 * Deliberately not the DTOs in `core.network.BankDtos`. Those carry the server's own enum
 * names as strings; these carry the decisions a screen actually makes - whether to mask an
 * input, whether to warn about a connection - so the UI never compares a string to
 * `"SECRET"` and never has to be edited when the server renames something.
 */

/**
 * One input in a provider's connect form.
 *
 * There is no per-bank form anywhere in this app. A provider describes what it needs and
 * the screen renders that description, which is what lets a new bank ship as a server
 * change alone.
 */
data class CredentialField(
    val name: String,
    val label: String,
    /** Masked while typing. A token is the usual case, which is why the server defaults to it. */
    val secret: Boolean,
    val placeholder: String? = null,
    val helpText: String? = null,
    val required: Boolean = true,
)

/**
 * How a bank lets the user prove the account is theirs.
 *
 * Anything the server grows later reads as [CREDENTIALS] here, which degrades to showing a
 * form with no fields rather than to a crash.
 */
enum class BankAuthMode {
    /** The user types what the bank gave them - see [CredentialField]. */
    CREDENTIALS,

    /** The user approves the request in the bank's own app - see [ConsentChallenge]. */
    CONSENT,
}

data class BankProvider(
    val id: String,
    val displayName: String,
    /** Where the user gets the credentials. Linked from the form when the provider gives one. */
    val helpUrl: String? = null,
    val authMode: BankAuthMode = BankAuthMode.CREDENTIALS,
    val credentialFields: List<CredentialField> = emptyList(),
)

/**
 * Something the user has to approve elsewhere, and how to put it in front of them.
 *
 * [approveUrl] is the bank's own: tapped on a phone it opens the bank's app, and on a
 * desktop the only way to reach that phone is [qrRows] - the same URL as a QR code, one
 * string per row with '1' where a module is dark.
 */
data class ConsentChallenge(
    val reference: String,
    val approveUrl: String,
    val qrRows: List<String> = emptyList(),
)

/**
 * The two states the server defines.
 *
 * Anything it grows later reads as [ACTIVE] here. The screen acts on [INVALID] alone -
 * it is the one state the user can fix, by supplying new credentials - so an unrecognised
 * status degrades to showing no warning rather than to a crash.
 */
enum class BankConnectionStatus { ACTIVE, INVALID }

/**
 * One set of credentials for one bank.
 *
 * No credential anywhere on it: they travel to the server and never come back. What is
 * left is enough to list the connection, warn when the bank has rejected it, and tell
 * which local accounts it feeds.
 */
data class BankConnection(
    val id: Long,
    val provider: String,
    val providerDisplayName: String,
    /** Whatever the bank calls the account holder. Null until a verify has reported one. */
    val label: String? = null,
    val status: BankConnectionStatus = BankConnectionStatus.ACTIVE,
    val linkedAccountIds: List<Long> = emptyList(),
)

/** An account as the bank describes it, before a local one has been pointed at it. */
data class RemoteAccount(
    val externalId: String,
    val displayName: String,
    val currencyCode: String,
    val balanceMinor: Long,
    /** The local account already linked to this one, if any. */
    val linkedAccountId: Long? = null,
)

/** One statement line, as it looks before the user has agreed to import it. */
data class PendingImport(
    val externalId: String,
    val date: LocalDate,
    val description: String,
    /** Positive magnitude; the direction is in [type], as everywhere else in the app. */
    val amountMinor: Long,
    val type: TransactionType,
    /** Already in the ledger. Shown greyed out, so the user can see it was not missed. */
    val alreadyImported: Boolean = false,
    /** The server's guess from the merchant category code. The user may override it. */
    val suggestedCategoryId: Long? = null,
)

data class ImportResult(val imported: Int, val skipped: Int)
