package com.personalmoney.core.billing

/**
 * One thing that can be bought, priced by the store in the user's own currency.
 *
 * [formattedPrice] comes from the store rather than from this app on purpose: it is the
 * only place that knows the local price, the local currency and how that currency is
 * written where the user is. Formatting it here would mean inventing all three.
 */
data class StoreProduct(
    val id: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    /**
     * "P1M", "P1Y" - the ISO-8601 period the price buys. Kept raw because the store's
     * word for it is the only reliable one; the screen turns it into a label.
     */
    val billingPeriod: String?
)

/** What came back from the store after asking to buy something. */
sealed interface PurchaseOutcome {
    /** Paid for. The token still has to be checked by the server before it means anything. */
    data class Bought(val purchaseToken: String, val productId: String) : PurchaseOutcome

    /** The user closed the dialog. Not an error, and nothing to show them about it. */
    data object Cancelled : PurchaseOutcome

    /**
     * Play took the order but has not settled it - a slow payment method, usually.
     *
     * Its own case rather than a failure because it will very likely become a purchase,
     * just not now, and telling the user it failed would send them to buy it twice.
     */
    data object Pending : PurchaseOutcome

    /**
     * The store refused. [message] is the store's own words where it has any, because
     * "billing error 6" helps nobody.
     */
    data class Failed(val message: String?) : PurchaseOutcome
}

/**
 * Buying, where the platform has a store.
 *
 * An interface with a platform implementation rather than an `expect class`, for the same
 * reason [com.personalmoney.core.data.KeyValueStore] is one: what backs it has nothing in
 * common between platforms. Android talks to Google Play Billing; a browser has no store
 * at all and says so through [available].
 *
 * Note what is *not* here: nothing that decides whether the user is premium. This only
 * gets a purchase token out of the store. What that token is worth is the server's
 * question, and asking it here would make the answer a client's opinion.
 */
interface StoreBilling {

    /** Whether this platform can buy anything. False in the browser. */
    val available: Boolean

    /** The store's own listing for [productIds], priced locally. Empty when unavailable. */
    suspend fun products(productIds: List<String>): List<StoreProduct>

    /** Opens the store's purchase dialog. Suspends until the user is done with it. */
    suspend fun buy(productId: String): PurchaseOutcome

    /**
     * Purchase tokens the store already holds for this device.
     *
     * How "restore purchases" works, and also what is checked on launch: a subscription
     * bought on another install is known to the store as soon as the same Google account
     * signs in, and the server can only learn about it if something hands it the token.
     */
    suspend fun ownedPurchases(): List<PurchaseOutcome.Bought>
}

/**
 * The browser's answer, and any Android build with billing switched off.
 *
 * A real implementation of "you cannot buy here" rather than a null: the paywall still
 * has a job on the web - showing what the account has and where to buy - and a nullable
 * dependency would put that branch in every call site.
 */
object UnavailableStoreBilling : StoreBilling {
    override val available: Boolean = false
    override suspend fun products(productIds: List<String>): List<StoreProduct> = emptyList()
    override suspend fun buy(productId: String): PurchaseOutcome =
        PurchaseOutcome.Failed(null)
    override suspend fun ownedPurchases(): List<PurchaseOutcome.Bought> = emptyList()
}
