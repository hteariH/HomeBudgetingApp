package com.homebudget.app.importer.homemoney

import com.homebudget.app.domain.CategoryType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * What an import would do, before it does any of it.
 *
 * The plan is a proposal the user edits: every account and category carries an
 * auto-matched target that can be pointed somewhere else or left as "create a new one".
 * Nothing here is persisted - it is recomputed on demand and handed back to `apply`.
 */
data class HomeMoneyPlan(
    val accounts: List<PlannedAccount> = emptyList(),
    val categories: List<PlannedCategory> = emptyList(),
    val transactionCount: Int = 0,
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
    /**
     * Why the import cannot run as proposed, in words the user can act on - today only
     * the free plan's account cap. Null when nothing is in the way.
     */
    val blockedReason: String? = null
) {
    val newAccountCount: Int get() = accounts.count { it.targetAccountId == null }
    val newCategoryCount: Int get() = categories.count { it.targetCategoryId == null }
}

/**
 * One HomeMoney account-and-currency pair, and where it should land.
 *
 * HomeMoney lets a single account hold several currencies at once; ours holds exactly
 * one. So the unit here is the pair, not the account, and a HomeMoney wallet holding
 * hryvnia and dollars becomes two accounts on our side.
 */
data class PlannedAccount(
    /** `homemoney:<accountId>:<currencyId>` - the identity the ledger rows point at. */
    val externalId: String,
    val homeMoneyAccountId: String,
    val homeMoneyCurrencyId: String,
    /** Suggested name; suffixed with the currency only when the account holds several. */
    val name: String,
    /** HomeMoney's own label for the currency, shown when it could not be resolved. */
    val currencyLabel: String,
    /** Resolved ISO code, or null when [currencyLabel] matched nothing in the catalog. */
    val currencyCode: String?,
    val balance: BigDecimal,
    /** An existing account to import into, or null to create one. */
    val targetAccountId: Long? = null,
    /** False when the user has excluded this account from the import. */
    val included: Boolean = true
)

data class PlannedCategory(
    /** `homemoney:<categoryId>`. */
    val externalId: String,
    val name: String,
    /** Includes the parent's name, which is what makes two "Fuel" categories tellable apart. */
    val fullName: String,
    val type: CategoryType,
    val parentExternalId: String?,
    val archived: Boolean,
    /** An existing category to reuse, or null to create one. */
    val targetCategoryId: Long? = null
)

/** How far an import has got. Polled by the client while [state] is RUNNING. */
data class HomeMoneyImportStatus(
    val state: State = State.IDLE,
    val accountsCreated: Int = 0,
    val categoriesCreated: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    /** Years walked so far, out of [totalChunks]; the only progress a range scan can give. */
    val processedChunks: Int = 0,
    val totalChunks: Int = 0,
    val error: String? = null
) {
    enum class State { IDLE, RUNNING, DONE, FAILED }
}

/**
 * HomeMoney's currency label to an ISO code.
 *
 * `BalanceList` returns `shortname`, which their own documentation calls a display name
 * rather than a code. Most of the time it already is the code and the catalog lookup
 * succeeds; this covers the symbols, and anything still unmatched is handed back to the
 * user to choose rather than guessed at - a wrong currency misstates a balance silently.
 */
object HomeMoneyCurrencyLabels {

    private val bySymbol = mapOf(
        "₴" to "UAH", "грн" to "UAH", "грн." to "UAH", "UAH" to "UAH",
        "$" to "USD", "US$" to "USD",
        "€" to "EUR",
        "£" to "GBP",
        "₽" to "RUB", "руб" to "RUB", "руб." to "RUB",
        "zł" to "PLN",
        "₺" to "TRY",
        "₸" to "KZT",
        "Kč" to "CZK",
        "Ft" to "HUF",
        "лв" to "BGN",
        "₾" to "GEL",
        "₿" to "BTC"
    )

    /** A code to try against the catalog, or null when the label says nothing useful. */
    fun toIsoCode(label: String): String? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        bySymbol[trimmed]?.let { return it }
        bySymbol.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.let { return it.value }
        // Three or four letters is what a ticker looks like; hand it on and let the
        // catalog have the final say.
        return if (trimmed.length in 3..4 && trimmed.all { it.isLetter() }) trimmed.uppercase() else null
    }
}
