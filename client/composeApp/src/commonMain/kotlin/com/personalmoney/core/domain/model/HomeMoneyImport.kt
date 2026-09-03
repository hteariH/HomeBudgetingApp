package com.personalmoney.core.domain.model

import com.personalmoney.core.domain.CategoryType
import kotlinx.datetime.LocalDate

/**
 * Moving a ledger in from homemoney.ua, as the rest of the app thinks about it.
 *
 * See [Account] for why none of this is a network type. The screen works in these, the
 * repository translates, and nothing above the repository knows what the wire looks like.
 */

/** Whether HomeMoney is linked, and to whom. Never carries a token; see `HomeMoneyDtos`. */
data class HomeMoneyConnection(
    val connected: Boolean,
    /** The HomeMoney account name, so the user can see whose data is about to arrive. */
    val label: String? = null,
    /** True once HomeMoney starts refusing the stored token and it has to be renewed. */
    val invalid: Boolean = false,
)

/**
 * One HomeMoney account-and-currency pair, and where the user wants it to land.
 *
 * The pair, not the account: HomeMoney lets one account hold several currencies at once
 * and ours holds exactly one, so a wallet with hryvnia and dollars in it arrives as two.
 */
data class HomeMoneyAccountMapping(
    val externalId: String,
    val name: String,
    /** HomeMoney's own word for the currency, which is all there is when [currencyCode] is null. */
    val currencyLabel: String,
    /**
     * Null when HomeMoney's label matched nothing in our catalog. The import refuses to
     * guess: the wrong currency is a wrong balance that nothing announces.
     */
    val currencyCode: String?,
    /** As HomeMoney reports it - kept as text so no rounding happens on the way through. */
    val balance: String,
    /** An account of the user's own to import into, or null to create a new one. */
    val targetAccountId: Long?,
    val included: Boolean,
) {
    val needsCurrency: Boolean get() = targetAccountId == null && currencyCode == null
}

data class HomeMoneyCategoryMapping(
    val externalId: String,
    val name: String,
    /** With the parent's name; what tells "Car / Fuel" from "Boat / Fuel". */
    val fullName: String,
    val type: CategoryType,
    val parentExternalId: String?,
    val archived: Boolean,
    /** An existing category to file under, or null to create one. */
    val targetCategoryId: Long?,
)

/** What the import would do, before the user has agreed to any of it. */
data class HomeMoneyImportPlan(
    val accounts: List<HomeMoneyAccountMapping> = emptyList(),
    val categories: List<HomeMoneyCategoryMapping> = emptyList(),
    val transactionCount: Int = 0,
    val firstDate: LocalDate? = null,
    val lastDate: LocalDate? = null,
    /** Why it cannot run as proposed - today only the free plan's account cap. */
    val blockedReason: String? = null,
) {
    val newAccountCount: Int get() = accounts.count { it.included && it.targetAccountId == null }
    val newCategoryCount: Int get() = categories.count { it.targetCategoryId == null }

    /** Accounts the user still has to pick a currency for; the import cannot start until they have. */
    val unresolvedCurrencies: List<HomeMoneyAccountMapping>
        get() = accounts.filter { it.included && it.needsCurrency }

    val canStart: Boolean
        get() = blockedReason == null &&
            unresolvedCurrencies.isEmpty() &&
            accounts.any { it.included }
}

/** How far the import has got. Polled while [running]. */
data class HomeMoneyImportProgress(
    val state: State = State.IDLE,
    val accountsCreated: Int = 0,
    val categoriesCreated: Int = 0,
    val imported: Int = 0,
    val skipped: Int = 0,
    /**
     * Years walked so far. Not a fraction of anything: HomeMoney cannot say how far back a
     * history goes, so the total is only known once the walk has finished.
     */
    val processedChunks: Int = 0,
    val error: String? = null,
) {
    enum class State { IDLE, RUNNING, DONE, FAILED }

    val running: Boolean get() = state == State.RUNNING
}
