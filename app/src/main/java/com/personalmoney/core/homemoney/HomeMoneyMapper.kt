package com.personalmoney.core.homemoney

import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money

/**
 * Pure mapping from a [BalanceListResponse] to a flat list of [ImportCandidate]s. Kept free of
 * Android/DB dependencies so it can be unit-tested against real wire samples.
 */
object HomeMoneyMapper {

    /**
     * @param existing set of (lower-cased account name, UPPER-cased currency code) pairs already in
     *   the DB, used to flag duplicates so a re-import doesn't silently double accounts.
     * @param resolveCode maps a HomeMoney `shortname` (often a symbol like "$"/"ft") to an ISO code.
     *   Defaults to a plain upper-case passthrough for tests that already pass ISO codes.
     */
    fun toCandidates(
        response: BalanceListResponse,
        existing: Set<Pair<String, String>> = emptySet(),
        resolveCode: (String) -> String = { it.trim().uppercase() },
    ): List<ImportCandidate> {
        // Pass 1: flatten group → account → currency into raw rows.
        val raws = mutableListOf<RawRow>()
        for (group in response.groups) {
            val groupName = group.name?.trim()?.takeIf { it.isNotEmpty() }
            for (account in group.accounts) {
                val accName = account.name?.trim().orEmpty()
                if (accName.isEmpty()) continue
                val lines = account.currencies.filterNot { it.shortname.isNullOrBlank() }
                val multiCurrency = lines.size > 1
                for (line in lines) {
                    val code = resolveCode(line.shortname!!)
                    raws += RawRow(
                        accountName = accName,
                        currencyCode = code,
                        balanceMinor = Money.fromDecimal(line.balance, CurrencyCode(code)).minor,
                        includeInTotal = account.includeInBalance,
                        groupName = groupName,
                        // "Visible in HomeMoney" requires both the account and this currency line shown.
                        visible = account.display && line.display,
                        baseName = if (multiCurrency) "$accName ($code)" else accName,
                    )
                }
            }
        }

        // Pass 2: within one import two accounts can share a name+currency (e.g. "Cash"/UAH in
        // different groups). Disambiguate the colliding ones by group so the created accounts — and
        // the next re-import's duplicate check — stay unambiguous.
        val keyCounts = raws.groupingBy { it.baseName.lowercase() to it.currencyCode }.eachCount()

        return raws.map { r ->
            val collides = (keyCounts[r.baseName.lowercase() to r.currencyCode] ?: 0) > 1
            val displayName =
                if (collides && r.groupName != null) "${r.baseName} · ${r.groupName}" else r.baseName
            // Dedup against the DB on the final display name OR the bare account name + currency.
            // The bare-name fallback catches the common case where an account that was single-currency
            // at first import (stored without a suffix) later gains a second currency (suffix appears).
            val exists = (displayName.lowercase() to r.currencyCode) in existing ||
                (r.accountName.lowercase() to r.currencyCode) in existing
            ImportCandidate(
                displayName = displayName,
                currencyCode = r.currencyCode,
                balanceMinor = r.balanceMinor,
                includeInTotal = r.includeInTotal,
                groupName = r.groupName,
                alreadyExists = exists,
                // Pre-check rows that are new, visible in HomeMoney, and actually hold money.
                defaultSelected = !exists && r.visible && r.balanceMinor != 0L,
            )
        }
    }

    private data class RawRow(
        val accountName: String,
        val currencyCode: String,
        val balanceMinor: Long,
        val includeInTotal: Boolean,
        val groupName: String?,
        val visible: Boolean,
        val baseName: String,
    )
}
