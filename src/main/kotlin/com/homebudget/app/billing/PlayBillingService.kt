package com.homebudget.app.billing

import com.homebudget.app.domain.EntitlementSource
import com.homebudget.app.domain.PlayPurchase
import com.homebudget.app.domain.User
import com.homebudget.app.repository.PlayPurchaseRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Turns a Google Play purchase into an entitlement.
 *
 * The phone buys through Play and gets a purchase token; it sends that token here, and
 * this asks Google what it bought. Nothing the client says about its own subscription is
 * taken at face value - the token is a receipt to be checked, not a claim to be believed.
 *
 * Renewals produce no request from the phone at all, which is why [recheckExpiring] runs
 * on a schedule: a subscription that renews next month has to keep working whether or not
 * the app is ever opened again.
 */
@Service
@Transactional
class PlayBillingService(
    private val api: PlayDeveloperApi,
    private val purchases: PlayPurchaseRepository,
    private val entitlements: EntitlementService,
    private val clock: Clock,
    /**
     * The subscription products that buy premium, most-preferred first.
     *
     * Configured rather than compiled in so a renamed or added base plan does not need an
     * app release on both sides - the client asks the server which products to show.
     */
    @Value("\${app.billing.play.products:premium_monthly,premium_yearly}")
    private val configuredProducts: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val products: List<String>
        get() = configuredProducts.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Whether the server can verify a purchase at all. False with no service-account key. */
    val available: Boolean get() = api.available

    /**
     * Checks a purchase token the phone just received and settles the entitlement on it.
     *
     * The token is written down against the account before anything is granted, so the
     * same receipt cannot be replayed onto a second account: a token already belonging to
     * somebody else is refused rather than quietly moved.
     */
    fun register(user: User, purchaseToken: String): PlayPurchase {
        if (!available) throw BillingUnavailableException("This server cannot verify Play purchases")
        val token = purchaseToken.trim()
        require(token.isNotEmpty()) { "The purchase token is empty" }

        val existing = purchases.findByTokenHash(PlayPurchase.hash(token))
        if (existing != null && existing.user.id != user.id) {
            log.warn("User {} presented a purchase token already held by user {}", user.id, existing.user.id)
            throw InvalidPurchaseException(
                "This purchase is already linked to another account. " +
                    "Sign in with the account that bought it, or contact support."
            )
        }

        val subscription = api.subscription(token)
        if (subscription.productId != null && subscription.productId !in products) {
            throw InvalidPurchaseException("This purchase is not a premium subscription")
        }

        val purchase = settle(existing ?: newPurchase(user, token, subscription), subscription)
        acknowledgeIfNeeded(purchase, subscription)
        return purchase
    }

    /**
     * Asks Google about a purchase again and moves the entitlement to match.
     *
     * Returns whether anything changed, so the scheduled sweep can say something useful
     * in the logs rather than only that it ran.
     */
    fun recheck(purchase: PlayPurchase): Boolean {
        val before = purchase.expiresAt to purchase.state
        val subscription = try {
            api.subscription(purchase.purchaseToken)
        } catch (e: InvalidPurchaseException) {
            // Google no longer recognises the token. That is an ended subscription, not a
            // reason to retry forever - record it and let the entitlement lapse.
            log.info("Play no longer recognises purchase {}; treating it as expired", purchase.id)
            PlaySubscription(
                state = "SUBSCRIPTION_STATE_EXPIRED",
                productId = purchase.productId,
                expiresAt = purchase.expiresAt,
                orderId = purchase.orderId,
                autoRenewing = false,
                acknowledged = purchase.acknowledged,
                test = false
            )
        }
        settle(purchase, subscription)
        return before != (purchase.expiresAt to purchase.state)
    }

    /** Every purchase this account has presented, newest first. */
    fun purchasesOf(user: User): List<PlayPurchase> =
        purchases.findAllByUser(user).sortedByDescending { it.createdAt }

    /**
     * Re-checks subscriptions that have reached their expiry.
     *
     * Hourly rather than daily: an expiry is a moment, not a date, and a subscription
     * that renewed at 09:00 should not spend the rest of the working day reading as
     * lapsed. The query only returns rows that are actually due, so most runs do nothing.
     */
    @Scheduled(cron = "\${app.billing.play.cron:0 20 * * * *}")
    fun recheckExpiring() {
        if (!available) return
        val now = Instant.now(clock)
        val due = purchases.findWorthRechecking(
            until = now.plus(RENEWAL_LOOKAHEAD),
            staleBefore = now.minus(MIN_RECHECK_INTERVAL)
        )
        if (due.isEmpty()) return

        var changed = 0
        for (purchase in due) {
            try {
                if (recheck(purchase)) changed++
            } catch (e: BillingUnavailableException) {
                // Google is having a bad minute. Leave checkedAt alone so the next run
                // picks the row up again, and do not touch the entitlement: an API that
                // cannot be reached is not evidence that somebody stopped paying.
                log.warn("Could not re-check purchase {}: {}", purchase.id, e.message)
            }
        }
        log.info("Re-checked {} Play purchases, {} changed", due.size, changed)
    }

    // -- internals -----------------------------------------------------------

    private fun newPurchase(user: User, token: String, subscription: PlaySubscription) = PlayPurchase(
        user = user,
        purchaseToken = token,
        productId = subscription.productId ?: products.firstOrNull() ?: "unknown",
        state = subscription.state,
        createdAt = Instant.now(clock),
        checkedAt = Instant.now(clock)
    )

    /** Writes what Google said onto the row, then moves the entitlement to match it. */
    private fun settle(purchase: PlayPurchase, subscription: PlaySubscription): PlayPurchase {
        subscription.productId?.let { purchase.productId = it }
        purchase.orderId = subscription.orderId ?: purchase.orderId
        purchase.state = subscription.state
        purchase.expiresAt = subscription.expiresAt
        purchase.autoRenewing = subscription.autoRenewing
        purchase.acknowledged = subscription.acknowledged || purchase.acknowledged
        purchase.checkedAt = Instant.now(clock)
        purchases.save(purchase)

        if (subscription.entitled) {
            entitlements.grant(purchase.user, subscription.expiresAt, EntitlementSource.PLAY, purchase.productId)
        } else {
            entitlements.revoke(purchase.user, EntitlementSource.PLAY, only = EntitlementSource.PLAY)
        }
        return purchase
    }

    /**
     * Play refunds a purchase left unacknowledged for three days, so this is not
     * optional - but it is also not worth failing the request over. The user has paid and
     * has been given what they paid for; a failed acknowledgement is picked up by the
     * next re-check, which finds `acknowledged` still false.
     */
    private fun acknowledgeIfNeeded(purchase: PlayPurchase, subscription: PlaySubscription) {
        if (purchase.acknowledged || !subscription.entitled) return
        try {
            api.acknowledge(purchase.productId, purchase.purchaseToken)
            purchase.acknowledged = true
            purchases.save(purchase)
        } catch (e: Exception) {
            log.warn("Could not acknowledge purchase {} with Play: {}", purchase.id, e.message)
        }
    }

    private companion object {
        /** How far past an expiry to keep looking, for a renewal Google records late. */
        val RENEWAL_LOOKAHEAD: Duration = Duration.ofMinutes(5)

        /** Never ask Google about the same token twice within this window. */
        val MIN_RECHECK_INTERVAL: Duration = Duration.ofMinutes(30)
    }
}
