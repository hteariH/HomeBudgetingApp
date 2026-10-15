package com.homebudget.app.ai

import com.homebudget.app.security.SecretCrypto
import jakarta.persistence.*
import java.time.Instant

/**
 * The model that reads receipts: one row for the instance (set by the administrator),
 * optionally one per household.
 */
@Entity
@Table(name = "ai_providers")
class AiProviderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val scope: AiProviderScope,

    /** Null for the instance row. */
    @Column(name = "household_id")
    val householdId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: AiProviderType,

    /** Only meaningful for [AiProviderType.OPENAI_COMPATIBLE]; the other two have fixed URLs. */
    @Column(name = "base_url", length = 500)
    var baseUrl: String? = null,

    @Column(nullable = false, length = 200)
    var model: String,

    /** Encrypted at rest; never returned by the API. */
    @Convert(converter = SecretCrypto::class)
    @Column(name = "api_key", length = 2048)
    var apiKey: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
