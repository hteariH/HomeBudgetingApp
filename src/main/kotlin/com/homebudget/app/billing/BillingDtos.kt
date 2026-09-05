package com.homebudget.app.billing

import com.homebudget.app.domain.Entitlement
import com.homebudget.app.domain.EntitlementSource
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * What the client is allowed to know about its own subscription.
 *
 * Deliberately not the [com.homebudget.app.domain.PlayPurchase] row: the purchase token
 * and the order id are between this server and Google, and a client has no use for
 * either. What a paywall needs is whether the account is premium, until when, and whether
 * buying is possible from here at all.
 */
data class BillingStatusResponse(
    val premium: Boolean,
    val expiresAt: Instant?,
    val source: EntitlementSource,
    /** True while Play will renew it. False on a subscription that is running out. */
    val autoRenewing: Boolean,
    val productId: String?,
    /**
     * Whether this server can verify a Play purchase.
     *
     * False on a deployment with no service-account key, and the reason the paywall can
     * say so instead of letting somebody pay into a checkout nothing is listening to.
     */
    val purchasesAccepted: Boolean,
    /** The subscription products to offer, in the order to show them. */
    val products: List<String>
)

/** Also served as [Entitlement] on `/api/users/me`; repeated here so a paywall needs one call. */
val BillingStatusResponse.entitlement: Entitlement
    get() = if (premium) Entitlement.PREMIUM else Entitlement.FREE

/**
 * A purchase token, straight from Play.
 *
 * Tokens are long - hundreds of characters - so the bound is generous. Anything longer is
 * not a token that came out of the billing library.
 */
data class RegisterPlayPurchaseRequest(
    @field:NotBlank @field:Size(max = 1024) val purchaseToken: String,
    /**
     * What the client believes it bought. Recorded in the log when it disagrees with
     * Google, because the disagreement is the interesting part; the entitlement is always
     * decided by Google's answer.
     */
    val productId: String? = null
)

/** One account, as an administrator needs to see it to decide about a grant. */
data class AdminUserResponse(
    val id: Long,
    val uuid: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val emailVerified: Boolean,
    val entitlement: Entitlement,
    val entitlementExpiresAt: Instant?,
    val entitlementSource: EntitlementSource,
    val admin: Boolean
)

/**
 * How long a manual grant runs.
 *
 * Both fields empty means indefinitely - the lifetime grant `V3` already describes. They
 * are mutually exclusive: [days] is the convenient form ("three months while they help us
 * test"), [until] the exact one.
 */
data class GrantPremiumRequest(
    val days: Long? = null,
    val until: Instant? = null,
    /** Why, for the log. A grant with no reason is one nobody can explain in a month. */
    val reason: String? = null
)
