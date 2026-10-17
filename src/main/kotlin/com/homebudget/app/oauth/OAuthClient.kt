package com.homebudget.app.oauth

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * An assistant that registered itself (RFC 7591). Public: it has no secret, so what binds a
 * code to it is the exact redirect URI and the PKCE challenge.
 */
@Entity
@Table(name = "oauth_clients")
class OAuthClient(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    val clientId: String,

    @Column(name = "client_name", nullable = false, length = 200)
    val clientName: String,

    /** Newline-separated, compared exactly. */
    @Column(name = "redirect_uris", nullable = false, length = 4000)
    val redirectUris: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
) {
    fun redirectUriList(): List<String> = redirectUris.split('\n')

    fun allowsRedirect(uri: String?): Boolean = uri != null && uri in redirectUriList()
}

interface OAuthClientRepository : JpaRepository<OAuthClient, Long> {
    fun findByClientId(clientId: String): OAuthClient?

    /**
     * Clients registered before [cutoff] that never obtained a token ([OAuthClient.lastUsedAt]
     * is set on every issue). Their grants and codes go with them (ON DELETE CASCADE).
     */
    @Modifying
    @Query("DELETE FROM OAuthClient c WHERE c.createdAt < :cutoff AND c.lastUsedAt IS NULL")
    fun deleteUnusedBefore(@Param("cutoff") cutoff: Instant): Int
}
