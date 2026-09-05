package com.personalmoney.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.personalmoney.core.util.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Buying through Google Play.
 *
 * The only payment rail this project can use: Play requires in-app purchases for digital
 * goods on Android anyway, and no card processor that would serve the web accepts a
 * Ukrainian sole trader - see `docs/PREMIUM.md` in the server repository.
 *
 * Nothing here decides that anybody is premium. The job is to come back with a purchase
 * token; whether that token bought anything is a question only the server can answer,
 * because only the server can ask Google.
 */
class PlayStoreBilling(
    context: Context,
    /**
     * The activity to draw the Play dialog over.
     *
     * A lookup rather than an injected activity: [PlayStoreBilling] is a singleton that
     * outlives every activity, and holding one would leak it. Null means nothing is on
     * screen, which is a real state - a purchase started from a notification tap, say -
     * and refusing then is better than crashing.
     */
    private val currentActivity: () -> Activity?
) : StoreBilling {

    /**
     * The purchase the user is in the middle of.
     *
     * Play reports the result through a listener rather than through the call that started
     * the flow, so this is what joins the two halves back together. [purchaseLock] keeps
     * it to one at a time - two dialogs cannot be open at once anyway, and without the
     * lock a second attempt would orphan the first one's waiter.
     */
    private var pending: CompletableDeferred<PurchaseOutcome>? = null
    private val purchaseLock = Mutex()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val outcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases.orEmpty().firstOrNull()?.toOutcome() ?: PurchaseOutcome.Cancelled

            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled

            // Already owned is not a failure worth showing: the subscription exists, and
            // what the app actually needs - the token - comes back from ownedPurchases().
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseOutcome.Cancelled

            else -> PurchaseOutcome.Failed(result.debugMessage.takeIf { it.isNotBlank() })
        }
        pending?.complete(outcome)
        pending = null
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        // Required by the builder even for a subscriptions-only app. Prepaid plans are
        // not enabled: this sells auto-renewing subscriptions, and a prepaid top-up would
        // arrive as a purchase the server's renewal logic does not model.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // Play's own reconnection with backoff, rather than one written here badly.
        .enableAutoServiceReconnection()
        .build()

    /**
     * What Play last told us about each product, kept because buying needs the offer
     * token that only [ProductDetails] carries - the product id alone will not do.
     */
    private val known = mutableMapOf<String, ProductDetails>()

    /**
     * True on Android, always.
     *
     * Not "this device can buy right now": that needs a connection to Play, which is a
     * suspend call, and a screen cannot ask a property for it. A device with no Play
     * Services gets an empty [products] list instead, which the paywall already handles -
     * it is the same state as a server that has listed no products.
     */
    override val available: Boolean = true

    override suspend fun products(productIds: List<String>): List<StoreProduct> {
        if (productIds.isEmpty() || !connect()) return emptyList()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()

        val result = runCatching { client.queryProductDetails(params) }
            .onFailure { AppLog.w(TAG, "Could not read products: ${it.message}") }
            .getOrNull()
            ?: return emptyList()

        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            AppLog.w(TAG, "Play refused the product query: ${result.billingResult.debugMessage}")
            return emptyList()
        }

        val details = result.productDetailsList.orEmpty()
        details.forEach { known[it.productId] = it }
        // In the order the server asked for, not the order Play answered in: the server
        // lists them cheapest-commitment first and that is how the paywall reads.
        return productIds.mapNotNull { id -> details.firstOrNull { it.productId == id }?.toStoreProduct() }
    }

    override suspend fun buy(productId: String): PurchaseOutcome = purchaseLock.withLock {
        val activity = currentActivity()
            ?: return PurchaseOutcome.Failed(null).also { AppLog.w(TAG, "No activity to buy over") }
        if (!connect()) return PurchaseOutcome.Failed(null)

        val details = known[productId]
            ?: products(listOf(productId)).let { known[productId] }
            ?: return PurchaseOutcome.Failed(null)
        val offer = details.bestOffer() ?: return PurchaseOutcome.Failed(null)

        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
            .build()

        val waiter = CompletableDeferred<PurchaseOutcome>()
        pending = waiter
        val launch = client.launchBillingFlow(activity, flow)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pending = null
            return PurchaseOutcome.Failed(launch.debugMessage.takeIf { it.isNotBlank() })
        }
        return waiter.await()
    }

    override suspend fun ownedPurchases(): List<PurchaseOutcome.Bought> {
        if (!connect()) return emptyList()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = runCatching { client.queryPurchasesAsync(params) }
            .onFailure { AppLog.w(TAG, "Could not read owned purchases: ${it.message}") }
            .getOrNull()
            ?: return emptyList()
        return result.purchasesList.mapNotNull { it.toOutcome() as? PurchaseOutcome.Bought }
    }

    /**
     * Connects if it is not connected already.
     *
     * `enableAutoServiceReconnection` handles a connection that drops later; this is only
     * about the first one, which nothing else would start.
     */
    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCoroutine { continuation ->
            var resumed = false
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (resumed) return
                    resumed = true
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (!ok) AppLog.w(TAG, "Play billing unavailable: ${result.debugMessage}")
                    continuation.resume(ok)
                }

                override fun onBillingServiceDisconnected() {
                    // Only interesting before the setup callback arrives - after that the
                    // continuation is spent and the client reconnects on its own.
                    if (resumed) return
                    resumed = true
                    continuation.resume(false)
                }
            })
        }
    }

    private fun Purchase.toOutcome(): PurchaseOutcome = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED ->
            PurchaseOutcome.Bought(purchaseToken, products.firstOrNull().orEmpty())

        Purchase.PurchaseState.PENDING -> PurchaseOutcome.Pending
        else -> PurchaseOutcome.Cancelled
    }

    /**
     * The offer to buy this product on.
     *
     * Play only returns offers the user is actually eligible for, so the cheapest opening
     * price is a free trial or an introductory rate where one applies and the plain base
     * plan where it does not - which is the offer anybody would pick for themselves.
     */
    private fun ProductDetails.bestOffer(): ProductDetails.SubscriptionOfferDetails? =
        subscriptionOfferDetails?.minByOrNull { it.openingPriceMicros() }

    private fun ProductDetails.SubscriptionOfferDetails.openingPriceMicros(): Long =
        pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros ?: Long.MAX_VALUE

    private fun ProductDetails.toStoreProduct(): StoreProduct {
        // The phase the user pays on renewal, not the trial: a yearly plan shown as "free"
        // because it opens with a trial tells them nothing about what it costs. The trial
        // itself is what `bestOffer` picks when they actually buy.
        val phase = bestOffer()?.pricingPhases?.pricingPhaseList?.lastOrNull()
        return StoreProduct(
            id = productId,
            // `name` is the product's own name; `title` appends the app name in brackets,
            // which reads badly inside the app it names.
            title = name.ifBlank { title },
            description = description,
            formattedPrice = phase?.formattedPrice.orEmpty(),
            billingPeriod = phase?.billingPeriod
        )
    }

    private companion object {
        const val TAG = "PlayStoreBilling"
    }
}
