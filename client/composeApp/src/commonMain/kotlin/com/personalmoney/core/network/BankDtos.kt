package com.personalmoney.core.network

import com.personalmoney.core.domain.TransactionType
import kotlinx.serialization.Serializable

/**
 * The bank-connection contract, captured from `BankController` in the server repository.
 *
 * Distinct from the sync and per-entity contracts: this is the one place the app talks to
 * someone else's bank on the user's behalf, and the shapes are driven by what the
 * providers need rather than by the app's own model.
 *
 * **Credentials go up and never come back.** `CreateConnectionRequest` carries them;
 * nothing in the response side of this file can hold one, which is deliberate - a DTO that
 * could carry a bank token is a DTO that will eventually be logged.
 */

/** What a provider needs before it can connect - the form is built from this, not hardcoded. */
@Serializable
data class RestCredentialField(
    val name: String,
    val label: String,
    /**
     * The server's own name for the input kind - `SECRET` for a token or password, which is
     * the one the form has to mask. Captured from a live response rather than guessed.
     */
    val type: String,
    val placeholder: String? = null,
    val helpText: String? = null,
    val required: Boolean = true,
)

@Serializable
data class RestBankProvider(
    val id: String,
    val displayName: String,
    val helpUrl: String? = null,
    val credentialFields: List<RestCredentialField> = emptyList(),
)

@Serializable
data class RestBankConnection(
    val id: Long,
    val provider: String,
    val providerDisplayName: String,
    val label: String? = null,
    /** The server's own enum name; the screen maps it to a colour and a message. */
    val status: String,
    val lastVerifiedAt: String? = null,
    val linkedAccountIds: List<Long> = emptyList(),
)

/** An account as the bank describes it, before the user has pointed a local one at it. */
@Serializable
data class RestRemoteAccount(
    val externalId: String,
    val displayName: String,
    val currencyCode: String,
    /** Already minor units here, unlike the money elsewhere in the API. */
    val balanceMinor: Long,
    val type: String? = null,
    val linkedAccountId: Long? = null,
)

/** One statement line the user has not imported yet. */
@Serializable
data class RestPendingImport(
    val externalId: String,
    val occurredAt: String,
    val date: String,
    val description: String,
    val amountMinor: Long,
    val type: TransactionType,
    val mcc: Int = 0,
    /** True when this line is already in the ledger - shown, but not re-imported. */
    val alreadyImported: Boolean = false,
    /** The server's guess from the MCC. The user can override it before confirming. */
    val suggestedCategoryId: Long? = null,
)

@Serializable
data class RestImportResult(val imported: Int, val skipped: Int)

// ── request bodies ───────────────────────────────────────────────────────────

@Serializable
data class CreateConnectionRequest(
    val provider: String,
    /** Keyed by [RestCredentialField.name]. */
    val credentials: Map<String, String> = emptyMap(),
)

@Serializable
data class UpdateCredentialsRequest(val credentials: Map<String, String> = emptyMap())

@Serializable
data class LinkAccountRequest(val externalAccountId: String)

@Serializable
data class ImportPreviewRequest(
    val accountId: Long,
    val dateFrom: String,
    val dateTo: String,
)

@Serializable
data class ImportConfirmRequest(
    val accountId: Long,
    val dateFrom: String,
    val dateTo: String,
    /** External transaction id to category id, or null to import it uncategorised. */
    val categories: Map<String, Long?> = emptyMap(),
)
