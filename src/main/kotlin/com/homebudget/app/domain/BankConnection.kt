package com.homebudget.app.domain

import com.homebudget.app.security.SecretCrypto
import jakarta.persistence.*
import java.time.LocalDateTime

enum class BankConnectionStatus {
    ACTIVE,

    /** The bank rejected the credentials; the user has to supply new ones. */
    INVALID
}

/**
 * One set of credentials for one bank, belonging to one user.
 *
 * Credentials live here rather than on the account because a single set usually covers
 * several accounts - one Monobank token sees every card and jar. Keeping a copy per
 * account duplicated the secret and left stale copies behind on revocation.
 *
 * Deliberately absent from the sync protocol: these secrets stay on the server and are
 * never sent to a device.
 */
@Entity
@Table(name = "bank_connections")
data class BankConnection(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    /** Selects the [com.homebudget.app.bank.BankProvider] implementation. */
    @Column(nullable = false, length = 30)
    var provider: String,

    /**
     * The provider's credential map as encrypted JSON. Opaque on purpose, so a bank
     * needing several fields costs no schema change.
     */
    @Convert(converter = SecretCrypto::class)
    @Column(nullable = false, length = 2048)
    var credentials: String,

    /** What the bank calls this customer, so several connections are tellable apart. */
    @Column
    var label: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BankConnectionStatus = BankConnectionStatus.ACTIVE,

    @Column(name = "last_verified_at")
    var lastVerifiedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
