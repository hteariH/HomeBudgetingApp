package com.homebudget.app.domain

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

enum class Role {
    USER, ADMIN
}

/**
 * What this account has paid for.
 *
 * Two values rather than a tier ladder: there is one thing to buy. A third tier would want
 * a table, not another constant, and inventing one before it is sold would be inventing a
 * price list.
 */
enum class Entitlement {
    FREE, PREMIUM
}

/**
 * How the current [Entitlement] was come by.
 *
 * Recorded so the two sources cannot undo each other. The nightly Play re-check has to be
 * able to take premium away when a subscription lapses; without knowing where the
 * entitlement came from it would also take away one handed out by hand, and a promotional
 * grant would quietly disappear overnight.
 */
enum class EntitlementSource {
    NONE, PLAY, MANUAL
}

/** What a [Entitlement.FREE] account may have. */
object PlanLimits {
    /**
     * Accounts, counting the ones actually in use.
     *
     * Archived accounts do not count. They are out of the picker and out of the net-worth
     * total already, and charging someone for tidying up after a bank account they closed
     * is the wrong way round. The trade is that the cap is on accounts in play rather than
     * rows in the table, which is also what "more than five accounts" means to a person.
     */
    const val FREE_ACCOUNTS = 5
}

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * The identifier handed to the payment provider - see `V3__add_entitlement.sql`.
     * Immutable: it is what a subscription is filed under, and changing it would orphan
     * the purchase.
     */
    @Column(nullable = false, length = 36, updatable = false)
    val uuid: String = UUID.randomUUID().toString(),

    @Column(unique = true, nullable = false)
    var email: String,

    @Column(nullable = false)
    var passwordHash: String,

    @Column(nullable = false)
    var firstName: String,

    @Column(nullable = false)
    var lastName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role = Role.USER,

    /**
     * Whether the address has been proven. Login is deliberately *not* gated on this -
     * a broken SMTP setup would otherwise lock every new user out - but password reset
     * is, since resetting to an unproven address hands the account to whoever owns it.
     */
    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    /**
     * Written only by the server - from a purchase Google Play confirmed, or from an
     * administrator's grant. Never by a client, and never by a request the user can
     * make, which is what makes it worth checking on the server.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var entitlement: Entitlement = Entitlement.FREE,

    /** When [entitlement] lapses. Null means it does not. */
    @Column(name = "entitlement_expires_at")
    var entitlementExpiresAt: Instant? = null,

    /** Which door [entitlement] came through - see [EntitlementSource]. */
    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_source", nullable = false, length = 20)
    var entitlementSource: EntitlementSource = EntitlementSource.NONE,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "base_currency_id")
    var baseCurrency: Currency? = null,

    @Column(name = "budget_cycle_start_day", nullable = false)
    var budgetCycleStartDay: Int = 1,

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Whether the subscription is live *now*, which is not the same as what the column says.
 *
 * A lapsed PREMIUM row stays PREMIUM until the provider gets around to telling us it
 * expired, and the webhook can be late or lost. Reading the expiry at the point of use
 * means a limit is enforced correctly even when no event ever arrives.
 */
fun User.isPremium(now: Instant = Instant.now()): Boolean =
    entitlement == Entitlement.PREMIUM && entitlementExpiresAt?.isAfter(now) != false
