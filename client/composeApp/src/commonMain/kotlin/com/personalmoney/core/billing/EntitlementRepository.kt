package com.personalmoney.core.billing

import com.personalmoney.core.data.UserPreferences
import com.personalmoney.core.network.BillingStatusDto
import com.personalmoney.core.network.HomeBudgetApi
import com.personalmoney.core.network.RegisterPurchaseRequestDto
import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Instant

/** Where an entitlement came from, as the server reports it. */
enum class EntitlementSource { NONE, PLAY, MANUAL }

/**
 * What the account is entitled to, and what it could buy.
 *
 * [purchasesAccepted] is the honest answer to "can I buy this here", and it is false far
 * more often than it looks: in the browser, on a server with no Play service account, and
 * on any build where Play Billing is not reachable.
 */
data class Entitlement(
    val premium: Boolean = false,
    val expiresAt: Instant? = null,
    val source: EntitlementSource = EntitlementSource.NONE,
    val autoRenewing: Boolean = false,
    val productId: String? = null,
    val purchasesAccepted: Boolean = false,
    val products: List<String> = emptyList()
) {
    /**
     * A subscription that has been cancelled but is still paid up.
     *
     * Worth naming because it is the one state where premium and "you are not going to
     * keep it" are both true, and the screen has something different to say about it.
     */
    val lapsing: Boolean get() = premium && !autoRenewing && expiresAt != null
}

/**
 * The account's entitlement, as the server sees it.
 *
 * Deliberately thin. It caches what the server said so a launch with no network does not
 * flash the free plan at a subscriber, and it hands purchase tokens over for checking -
 * but it decides nothing. Every limit is enforced on the server, so the worst a tampered
 * cache can do is show the wrong screen for a moment.
 *
 * [syncOwnedPurchases] is the piece that is easy to leave out and then miss: a
 * subscription bought on a previous install is known to Play the moment the same Google
 * account signs in, and the server can only learn about it if something hands over the
 * token. Running it on launch is what makes reinstalling, or a second device, just work.
 */
class EntitlementRepository(
    private val api: HomeBudgetApi,
    private val store: StoreBilling,
    private val preferences: UserPreferences
) {
    private val state = MutableStateFlow(Entitlement())

    /** The live answer: the server's if it has been reached, the cache's until then. */
    val entitlement: StateFlow<Entitlement> = state.asStateFlow()

    /**
     * Premium as the app should *show* it, cache included. **Null means not known yet.**
     *
     * Separate from [entitlement] because the two answer different questions: this one is
     * "hide the ads and the upsell", which has to work before the first request comes
     * back, while [entitlement] is "what exactly does this account have", which does not.
     *
     * The null is load-bearing rather than tidy. The cached answer arrives from storage a
     * moment after launch, and a plain `false` in the meantime would show a subscriber the
     * banner they paid to be rid of - so callers are made to say what they do while nobody
     * knows, and every one of them chooses to show nothing.
     */
    val premium: Flow<Boolean?> = combine(
        state,
        preferences.premium.map<Boolean, Boolean?> { it }.onStart { emit(null) }
    ) { live, cached ->
        if (live.source != EntitlementSource.NONE || live.premium) live.premium else cached
    }

    /** Re-reads the entitlement from the server. Silent on failure - this is never urgent. */
    suspend fun refresh(): Entitlement? = runCatching { api.billingStatus() }
        .onFailure { AppLog.w(TAG, "Could not read the billing status: ${it.message}") }
        .getOrNull()
        ?.let { adopt(it) }

    /**
     * Hands a purchase token to the server and adopts the entitlement it reports back.
     *
     * Failure is rethrown rather than swallowed: this one runs because the user pressed a
     * button and paid money, and a paywall that quietly does nothing is the worst possible
     * outcome of that.
     */
    suspend fun submit(purchase: PurchaseOutcome.Bought): Entitlement =
        adopt(api.registerPurchase(RegisterPurchaseRequestDto(purchase.purchaseToken, purchase.productId)))

    /**
     * Tells the server about every purchase the store already holds for this device.
     *
     * Safe to call on every launch: presenting a token the server already knows re-checks
     * it rather than duplicating anything, and a token the store no longer holds is simply
     * not sent.
     */
    suspend fun syncOwnedPurchases() {
        if (!store.available) return
        val owned = runCatching { store.ownedPurchases() }
            .onFailure { AppLog.w(TAG, "Could not read owned purchases: ${it.message}") }
            .getOrDefault(emptyList())
        for (purchase in owned) {
            runCatching { submit(purchase) }
                .onFailure { AppLog.w(TAG, "The server would not take a held purchase: ${it.message}") }
        }
        if (owned.isEmpty()) refresh()
    }

    /** Asks the server to go back to Google now, for "I have paid and it still says free". */
    suspend fun forceRecheck(): Entitlement = adopt(api.refreshPurchases())

    private suspend fun adopt(dto: BillingStatusDto): Entitlement {
        val entitlement = dto.toEntitlement()
        state.value = entitlement
        preferences.setPremium(entitlement.premium, entitlement.expiresAt?.toEpochMilliseconds())
        return entitlement
    }

    private companion object {
        const val TAG = "EntitlementRepository"
    }
}

private fun BillingStatusDto.toEntitlement() = Entitlement(
    premium = premium,
    // An expiry this cannot parse is treated as no expiry rather than as an expired
    // subscription: the server has already decided `premium`, and this field is only
    // ever displayed.
    expiresAt = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    source = runCatching { EntitlementSource.valueOf(source) }.getOrDefault(EntitlementSource.NONE),
    autoRenewing = autoRenewing,
    productId = productId,
    purchasesAccepted = purchasesAccepted,
    products = products
)
