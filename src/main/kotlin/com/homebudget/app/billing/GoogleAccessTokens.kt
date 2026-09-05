package com.homebudget.app.billing

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Access tokens for the Google Play Developer API, minted from a service-account key.
 *
 * Hand-rolled rather than pulled from `google-auth-library`, which would drag in the
 * whole google-http-client stack for one signed assertion and one form POST. jjwt is
 * already here for this app's own tokens and signs RS256 just as well.
 *
 * The flow is Google's two-legged OAuth: sign a JWT that says who we are and what scope
 * we want, hand it to the token endpoint, get an access token back. Tokens last an hour,
 * so they are cached - re-minting one per purchase check would be a needless round trip
 * on every call.
 */
@Component
class GoogleAccessTokens(
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    /**
     * The service-account key, either as the JSON itself or as a path to the file.
     *
     * Both are accepted because both are how it arrives: a JSON blob in an environment
     * variable on the VPS, a mounted file in a container. Empty means Play verification
     * is switched off, which is the state of a development machine.
     */
    @Value("\${app.billing.play.service-account:}") private val serviceAccount: String,
    @Value("\${app.billing.play.token-url:https://oauth2.googleapis.com/token}")
    private val tokenUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http: RestClient by lazy { RestClient.create() }

    /** Whether a key was configured at all. Nothing here works without one. */
    val configured: Boolean get() = serviceAccount.isNotBlank()

    private val credentials: Credentials by lazy { readCredentials() }

    @Volatile private var cached: CachedToken? = null

    /**
     * A bearer token for `androidpublisher`, minted or reused.
     *
     * Refreshed a minute before it actually expires: a token that dies mid-flight comes
     * back as a 401 that looks like a permissions problem, and that is a bad hour to
     * spend.
     */
    @Synchronized
    fun accessToken(): String {
        val now = Instant.now(clock)
        cached?.let { if (it.expiresAt.isAfter(now.plusSeconds(60))) return it.value }

        val assertion = Jwts.builder()
            .issuer(credentials.clientEmail)
            .audience().add(tokenUrl).and()
            .claim("scope", SCOPE)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ASSERTION_TTL_SECONDS)))
            .signWith(credentials.privateKey, Jwts.SIG.RS256)
            .compact()

        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            add("assertion", assertion)
        }

        val body = http.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("Google's token endpoint returned no body")

        val json = objectMapper.readTree(body)
        val token = json.path("access_token").asText(null)
            ?: throw IllegalStateException("Google's token endpoint returned no access_token: $body")
        val ttl = json.path("expires_in").asLong(3600)

        cached = CachedToken(token, now.plusSeconds(ttl))
        log.debug("Minted a Play API access token, good for {}s", ttl)
        return token
    }

    private fun readCredentials(): Credentials {
        check(configured) { "app.billing.play.service-account is not set" }
        val json = if (serviceAccount.trimStart().startsWith("{")) {
            serviceAccount
        } else {
            Files.readString(Path.of(serviceAccount))
        }
        val node = objectMapper.readTree(json)
        val email = node.path("client_email").asText(null)
            ?: throw IllegalStateException("The service-account key has no client_email")
        val pem = node.path("private_key").asText(null)
            ?: throw IllegalStateException("The service-account key has no private_key")
        return Credentials(email, parsePrivateKey(pem))
    }

    /**
     * PKCS#8 PEM to a key.
     *
     * The newlines matter and are the usual thing to get wrong: a key pasted into an
     * environment variable often arrives with literal `\n` two-character sequences, so
     * those are turned back into line breaks before the base64 body is decoded.
     */
    private fun parsePrivateKey(pem: String): PrivateKey {
        val body = pem
            .replace("\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filterNot { it.isWhitespace() }
        val der = Base64.getDecoder().decode(body)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private data class Credentials(val clientEmail: String, val privateKey: PrivateKey)

    private data class CachedToken(val value: String, val expiresAt: Instant)

    private companion object {
        const val SCOPE = "https://www.googleapis.com/auth/androidpublisher"

        /**
         * Google caps the assertion's own lifetime at an hour; it is only alive long
         * enough to be exchanged, so shorter is free.
         */
        const val ASSERTION_TTL_SECONDS = 300L
    }
}
