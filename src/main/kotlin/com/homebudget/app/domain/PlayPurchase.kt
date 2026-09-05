package com.homebudget.app.domain

import jakarta.persistence.*
import java.security.MessageDigest
import java.time.Instant

/**
 * A Google Play subscription purchase this server has checked with Google.
 *
 * Kept because the entitlement columns on [User] cannot answer two questions on their
 * own: which purchase is paying for this account (so a renewal can be picked up without
 * the phone asking), and whether a token has already been claimed by somebody else.
 *
 * Not [Syncable] - it is between the server and Google, and no client has any business
 * holding a copy of a purchase token.
 */
@Entity
@Table(name = "play_purchases")
class PlayPurchase(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "purchase_token", nullable = false, length = 1024)
    val purchaseToken: String,

    /** SHA-256 of [purchaseToken] - see the note in `V5__play_purchases.sql`. */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    val tokenHash: String = hash(purchaseToken),

    @Column(name = "product_id", nullable = false, length = 191)
    var productId: String,

    @Column(name = "order_id", length = 191)
    var orderId: String? = null,

    @Column(nullable = false, length = 64)
    var state: String,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "auto_renewing", nullable = false)
    var autoRenewing: Boolean = false,

    @Column(nullable = false)
    var acknowledged: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /** When Google was last asked about this token. */
    @Column(name = "checked_at", nullable = false)
    var checkedAt: Instant = Instant.now()
) {
    companion object {
        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
