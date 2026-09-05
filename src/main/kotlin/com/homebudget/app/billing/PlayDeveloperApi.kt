package com.homebudget.app.billing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant

/**
 * The purchase is not valid - a token Google does not recognise, or one that never
 * belonged to this app. The request is wrong, not the server.
 */
class InvalidPurchaseException(message: String) : IllegalArgumentException(message)

/** Google could not be reached, or answered with something unusable. Try again later. */
class BillingUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * What Google says about one subscription purchase.
 *
 * Only the fields an entitlement decision needs. The real response is far larger and
 * mostly about billing history, which belongs in the Play Console rather than here.
 */
data class PlaySubscription(
    val state: String,
    val productId: String?,
    val expiresAt: Instant?,
    val orderId: String?,
    val autoRenewing: Boolean,
    val acknowledged: Boolean,
    val test: Boolean
) {
    /**
     * Whether this purchase currently pays for premium.
     *
     * CANCELED counts: cancelling turns auto-renewal off, it does not refund the period
     * already paid for, and taking the app away the moment somebody cancels would be
     * charging them for time they never get. The grace period counts for the same reason -
     * Google is still retrying the card and the subscription is live meanwhile. ON_HOLD
     * and PAUSED do not: there, access is meant to stop.
     */
    val entitled: Boolean get() = state in ENTITLED_STATES

    private companion object {
        val ENTITLED_STATES = setOf(
            "SUBSCRIPTION_STATE_ACTIVE",
            "SUBSCRIPTION_STATE_CANCELED",
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD"
        )
    }
}

/**
 * The slice of the Google Play Developer API that verifying a subscription needs.
 *
 * An interface, not a class, for the same reason `MailService` is one: tests substitute an
 * implementation that answers from a script rather than mocking a class the JDK will not
 * let Mockito instrument.
 */
interface PlayDeveloperApi {

    /** Whether purchases can be verified at all. False with no service-account key. */
    val available: Boolean

    /** What Google says the token bought. */
    fun subscription(purchaseToken: String): PlaySubscription

    /** Tells Play the purchase has been honoured. */
    fun acknowledge(productId: String, purchaseToken: String)
}

/**
 * Asks Google.
 *
 * Verification happens here rather than on the phone because a client reporting its own
 * subscription is not a check. The phone hands over the purchase token Play gave it; this
 * asks Google what that token actually bought.
 *
 * `purchases.subscriptionsv2.get` rather than the v1 `purchases.subscriptions.get`: v1 is
 * deprecated and does not understand base plans, so it cannot answer correctly about a
 * subscription that has more than one. Acknowledgement is still a v1 call - v2 has no
 * equivalent - and it is not optional: Play refunds any purchase left unacknowledged for
 * three days.
 */
@Component
class GooglePlayDeveloperApi(
    private val tokens: GoogleAccessTokens,
    private val objectMapper: ObjectMapper,
    @Value("\${app.billing.play.package-name:com.personalmoney}") private val packageName: String,
    @Value("\${app.billing.play.base-url:https://androidpublisher.googleapis.com}")
    private val baseUrl: String
) : PlayDeveloperApi {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http: RestClient by lazy { RestClient.builder().baseUrl(baseUrl).build() }

    override val available: Boolean get() = tokens.configured

    override fun subscription(purchaseToken: String): PlaySubscription {
        val body = get(
            "/androidpublisher/v3/applications/$packageName/purchases/subscriptionsv2/tokens/$purchaseToken"
        )
        return parse(objectMapper.readTree(body))
    }

    override fun acknowledge(productId: String, purchaseToken: String) {
        val uri = "/androidpublisher/v3/applications/$packageName/purchases/subscriptions/" +
            "$productId/tokens/$purchaseToken:acknowledge"
        http.post()
            .uri(uri)
            .header("Authorization", "Bearer ${tokens.accessToken()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .retrieve()
            .onStatus({ it.is4xxClientError }, { _, response -> throw refusal(response.statusCode, uri) })
            .toBodilessEntity()
    }

    private fun get(uri: String): String = try {
        http.get()
            .uri(uri)
            .header("Authorization", "Bearer ${tokens.accessToken()}")
            .retrieve()
            .onStatus({ it.is4xxClientError }, { _, response -> throw refusal(response.statusCode, uri) })
            .body(String::class.java)
            ?: throw BillingUnavailableException("Google returned an empty response")
    } catch (e: InvalidPurchaseException) {
        throw e
    } catch (e: BillingUnavailableException) {
        throw e
    } catch (e: Exception) {
        throw BillingUnavailableException("Could not reach Google Play: ${e.message}", e)
    }

    /**
     * A 4xx from Google is about the purchase, not about us - with one exception worth
     * separating, because the two look identical from the client and lead opposite ways.
     * 401 and 403 mean *our* service account cannot see this app, which is a deployment
     * problem; telling the user their purchase is invalid would send them to support over
     * something only we can fix.
     */
    private fun refusal(status: HttpStatusCode, uri: String): RuntimeException {
        log.warn("Play API answered {} for {}", status, uri.substringBefore("/tokens/"))
        return if (status.value() == 401 || status.value() == 403) {
            BillingUnavailableException("The Play service account is not authorised for $packageName")
        } else {
            InvalidPurchaseException("Google Play does not recognise this purchase")
        }
    }

    private fun parse(node: JsonNode): PlaySubscription {
        val lineItems = node.path("lineItems")
        // The latest expiry across line items: a purchase with several base plans is
        // entitled until the last of them runs out, not the first.
        val expiry = lineItems.mapNotNull { it.path("expiryTime").asText(null)?.toInstantOrNull() }.maxOrNull()
        val autoRenewing = lineItems.any { it.path("autoRenewingPlan").path("autoRenewEnabled").asBoolean(false) }
        return PlaySubscription(
            state = node.path("subscriptionState").asText("SUBSCRIPTION_STATE_UNSPECIFIED"),
            productId = lineItems.firstOrNull()?.path("productId")?.asText(null),
            expiresAt = expiry,
            orderId = node.path("latestOrderId").asText(null),
            autoRenewing = autoRenewing,
            acknowledged = node.path("acknowledgementState").asText() == "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
            test = node.has("testPurchase")
        )
    }

    private fun String.toInstantOrNull(): Instant? = try {
        Instant.parse(this)
    } catch (e: Exception) {
        log.warn("Play sent an expiry this cannot read: {}", this)
        null
    }
}
