package com.personalmoney.core.homemoney

/**
 * One importable line derived from the HomeMoney balance tree: a single (account, currency) pair
 * that maps to one local [com.personalmoney.core.database.entity.AccountEntity]. A multi-currency
 * HomeMoney account yields several candidates, disambiguated by a "(CODE)" suffix on [displayName].
 */
data class ImportCandidate(
    val displayName: String,
    val currencyCode: String,
    val balanceMinor: Long,
    val includeInTotal: Boolean,
    val groupName: String?,
    /** True when an account with this name+currency already exists locally. */
    val alreadyExists: Boolean,
    /** Whether the row should be pre-checked in the preview (new, non-zero, visible). */
    val defaultSelected: Boolean,
)

/**
 * Result of fetching the HomeMoney balance tree: the account holder's name (for a confirmation
 * line on the preview) plus the flattened import candidates.
 */
data class FetchResult(
    val accountHolder: String?,
    val candidates: List<ImportCandidate>,
)

/**
 * Raised when HomeMoney returns an error envelope (bad credentials, invalid token, etc.).
 * [code] is the API's numeric error code when present.
 */
class HomeMoneyException(
    val serverMessage: String?,
    val code: Int? = null,
) : Exception(serverMessage ?: "HomeMoney error${code?.let { " ($it)" } ?: ""}")
