package com.personalmoney.core.network

import com.personalmoney.core.domain.CategoryType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The HomeMoney import contract, mirroring `HomeMoneyImportController` on the server.
 *
 * Distinct from the bank contract next door even though both import from somewhere else.
 * A bank import adds a date range of one account's statement to a ledger that already
 * exists; this one *builds* the ledger - accounts, the category tree, every operation, and
 * the balances they have to add up to. So the thing the user confirms is not "which
 * category is this line" but "is this HomeMoney account the same thing as that account I
 * already have".
 *
 * **No credential ever appears in this file.** The OAuth round trip happens between the
 * browser, homemoney.ua and our server; the app only ever learns whether it worked.
 *
 * Money is a string, as everywhere else on this API - see the note at the top of
 * `RestDtos`.
 */

@Serializable
data class RestHomeMoneyConnection(
    val connected: Boolean = false,
    /** The HomeMoney account name, so the user can see whose data is about to arrive. */
    val label: String? = null,
    /** The server's own enum name: ACTIVE, or INVALID once HomeMoney starts refusing. */
    val status: String? = null,
    val lastVerifiedAt: String? = null,
)

/**
 * Where to send the browser, and the state to hold on to while it is gone.
 *
 * homemoney.ua accepts the `state` parameter and does not return it - their redirect
 * arrives with `code` alone. So the app keeps this copy across the page reload and sends
 * it back with the code; it is what proves this browser started the round trip.
 */
@Serializable
data class RestHomeMoneyAuthorization(val authorizationUrl: String, val state: String = "")

/** The code the redirect left in the URL, on its way back to the server to be spent. */
@Serializable
data class HomeMoneyCodeRequest(val code: String, val state: String)

/**
 * [EncodeDefault] on the two fields whose default is not the harmless answer.
 *
 * kotlinx.serialization omits any value equal to its declared default, so an account the
 * user left switched on - `included = true`, the default - travelled with no `included`
 * field at all, and Jackson filled the gap on the other side with `false`. Every account
 * arrived excluded and an import that reported success brought in nothing. The server's
 * DTO now defaults to `true` as well, so the two agree either way; this makes the wire say
 * what it means rather than relying on that.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RestPlannedAccount(
    /** `homemoney:<account>:<currency>`; the identity every imported row points at. */
    val externalId: String,
    val name: String,
    /** HomeMoney's own label for the currency, shown when it resolved to nothing. */
    val currencyLabel: String,
    /** Null when the label matched no currency in the catalog; the user has to choose. */
    val currencyCode: String? = null,
    @EncodeDefault val balance: String = "0",
    /** An existing account to import into, or null to create one. */
    val targetAccountId: Long? = null,
    @EncodeDefault val included: Boolean = true,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RestPlannedCategory(
    val externalId: String,
    val name: String,
    /** With the parent's name, which is what tells two "Fuel" categories apart. */
    val fullName: String,
    val type: CategoryType,
    val parentExternalId: String? = null,
    /** Encoded even when false; see [RestPlannedAccount]. */
    @EncodeDefault val archived: Boolean = false,
    val targetCategoryId: Long? = null,
)

@Serializable
data class RestHomeMoneyPlan(
    val accounts: List<RestPlannedAccount> = emptyList(),
    val categories: List<RestPlannedCategory> = emptyList(),
    val transactionCount: Int = 0,
    val firstDate: String? = null,
    val lastDate: String? = null,
    val newAccountCount: Int = 0,
    val newCategoryCount: Int = 0,
    /** Non-null means it cannot run as proposed, and says why in a sentence. */
    val blockedReason: String? = null,
)

/** The plan as the user left it. Sent back whole; see the server's own note on why. */
@Serializable
data class HomeMoneyApplyRequest(
    val accounts: List<RestPlannedAccount> = emptyList(),
    val categories: List<RestPlannedCategory> = emptyList(),
)

@Serializable
data class RestHomeMoneyStatus(
    val state: String = "IDLE",
    val accountsCreated: Int = 0,
    val categoriesCreated: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    val processedChunks: Int = 0,
    val totalChunks: Int = 0,
    val error: String? = null,
)
