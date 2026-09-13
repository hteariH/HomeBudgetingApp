package com.homebudget.app.bank

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.homebudget.app.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The parts of Monobank both of its APIs share.
 *
 * Monobank exposes the same `/personal` endpoints twice: once authenticated with a token
 * the customer issues themselves, and once with a signature from a company the bank has
 * approved. Only the headers differ - the paths, the JSON and the rate limits are one API
 * - so the wire types, the mapping and the window rule live here, and each provider brings
 * its own authentication.
 *
 * @see MonobankPersonalProvider
 * @see MonobankCorporateProvider
 */
internal const val MONO_API = "https://api.monobank.ua"

/**
 * Monobank refuses a statement wider than 31 days and an hour.
 *
 * Checked before the request rather than after, because the request that would have been
 * rejected still costs the caller its minute of rate limit.
 */
internal const val MONO_MAX_STATEMENT_SECONDS = 2_682_000L

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class MonoAccount(
    val id: String = "",
    val currencyCode: Int = 980,
    val balance: Long = 0,
    val creditLimit: Long = 0,
    val type: String = "black",
    val iban: String = "",
    val maskedPan: List<String> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class MonoClientInfo(
    val clientId: String = "",
    val name: String = "",
    val accounts: List<MonoAccount> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class MonoStatement(
    val id: String = "",
    val time: Long = 0,
    val description: String = "",
    val mcc: Int = 0,
    val amount: Long = 0,
    val operationAmount: Long = 0,
    val currencyCode: Int = 980,
    val balance: Long = 0,
    /** What the customer typed on a transfer. Absent on card purchases. */
    val comment: String? = null
)

internal fun MonoAccount.toRemoteAccount() = RemoteAccount(
    externalId = id,
    // A card shows its masked number, a current account its IBAN's last digits; the type
    // alone ("black", "fop") repeats across accounts and cannot tell two of them apart.
    displayName = maskedPan.firstOrNull()
        ?: iban.takeIf { it.length > 4 }?.let { "···${it.takeLast(4)}" }
        ?: type,
    currencyCode = IsoCurrency.codeOf(currencyCode),
    // Monobank reports minor units already; the target account's own scale is applied
    // when the import writes the row.
    balanceMinor = balance,
    type = type
)

internal fun MonoStatement.toRemoteTransaction() = RemoteTransaction(
    externalId = id,
    occurredAt = Instant.ofEpochSecond(time),
    // The customer's own comment says more than "From: Ivan M." does, when there is one.
    description = listOfNotNull(
        description.takeIf { it.isNotBlank() },
        comment?.takeIf { it.isNotBlank() && it != description }
    ).joinToString(" · ").ifBlank { "—" },
    amountMinor = Math.abs(amount),
    currencyCode = IsoCurrency.codeOf(currencyCode),
    type = if (amount < 0) TransactionType.EXPENSE else TransactionType.INCOME,
    mcc = mcc
)

/**
 * The statement path's `from`/`to`, as epoch seconds.
 *
 * `to` is the end of the closing day rather than its start, so a range of one day is the
 * whole of that day - the boundary a user picking two dates means.
 */
internal fun monoStatementWindow(from: LocalDate, to: LocalDate): Pair<Long, Long> {
    val fromEpoch = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
    val toEpoch = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
    if (toEpoch <= fromEpoch) {
        throw BankRequestException("The statement range ends before it starts")
    }
    if (toEpoch - fromEpoch > MONO_MAX_STATEMENT_SECONDS) {
        throw BankRequestException("Monobank only returns 31 days of statement at a time")
    }
    return fromEpoch to toEpoch
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
