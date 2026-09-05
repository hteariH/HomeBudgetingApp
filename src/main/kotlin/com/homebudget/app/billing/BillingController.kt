package com.homebudget.app.billing

import com.homebudget.app.domain.PlayPurchase
import com.homebudget.app.domain.User
import com.homebudget.app.domain.isPremium
import com.homebudget.app.security.SecurityUtils
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * What the paywall talks to.
 *
 * Three calls, and only one of them writes: what am I entitled to, here is a receipt, and
 * check my receipt again. Buying itself happens in the Play dialog on the device - this
 * server never sees a card and never asks for one.
 */
@RestController
@RequestMapping("/api/billing")
class BillingController(
    private val billing: PlayBillingService,
    private val entitlements: EntitlementService,
    private val securityUtils: SecurityUtils
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * What this account is entitled to, and what it could buy.
     *
     * Answers on every platform, including the web, where nothing can be bought: the
     * paywall still has to show what the account has, and `purchasesAccepted` is how it
     * knows to say "buy this in the Android app" rather than offering a button that
     * cannot work.
     */
    @GetMapping("/status")
    fun status(): BillingStatusResponse = statusOf(securityUtils.getCurrentUser())

    /**
     * Hands over the purchase token Play just returned.
     *
     * Called after a successful purchase and again on every launch for a subscription
     * the client still holds locally, which is what makes it double as "restore
     * purchases": presenting a token that is still good re-grants the entitlement,
     * whatever this server had recorded.
     */
    @PostMapping("/play/purchase")
    fun registerPurchase(@Valid @RequestBody req: RegisterPlayPurchaseRequest): BillingStatusResponse {
        val user = securityUtils.getCurrentUser()
        val purchase = billing.register(user, req.purchaseToken)
        if (req.productId != null && req.productId != purchase.productId) {
            log.info(
                "Client reported product {} for purchase {}, Play says {}",
                req.productId, purchase.id, purchase.productId
            )
        }
        return statusOf(user, purchase)
    }

    /**
     * Re-asks Google about this account's purchases, now.
     *
     * The hourly sweep already does this, and normally there is no reason to call it -
     * but "I have paid and the app still says free" is a support conversation worth being
     * able to end in one tap.
     */
    @PostMapping("/play/refresh")
    fun refresh(): BillingStatusResponse {
        val user = securityUtils.getCurrentUser()
        if (billing.available) {
            billing.purchasesOf(user).forEach { billing.recheck(it) }
        }
        return statusOf(user)
    }

    private fun statusOf(user: User, known: PlayPurchase? = null): BillingStatusResponse {
        // The purchase that decides the status is the one that runs longest, not the most
        // recent: buying a year while a month still has days left leaves two live rows.
        val purchase = known ?: billing.purchasesOf(user)
            .filter { it.expiresAt != null }
            .maxByOrNull { it.expiresAt!! }
        return BillingStatusResponse(
            premium = user.isPremium(entitlements.now()),
            expiresAt = user.entitlementExpiresAt,
            source = user.entitlementSource,
            autoRenewing = purchase?.autoRenewing ?: false,
            productId = purchase?.productId,
            purchasesAccepted = billing.available,
            products = billing.products
        )
    }
}
