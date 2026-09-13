package com.homebudget.app.bank

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.sec.SECObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.AnnotatedTypeMetadata
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.util.Base64

/**
 * How this deployment identifies itself to Monobank as an approved company.
 *
 * Both values come from Monobank's registration, which is a one-off piece of operator
 * work rather than anything a user does - see `docs/monobank-corporate.md`. Absent, and
 * [MonobankCorporateProvider] is not registered at all, so the bank list simply does not
 * offer it. That is deliberate: a self-hosted copy of this app has no company and no key,
 * and an offer that could only ever fail is worse than no offer.
 */
@ConfigurationProperties(prefix = "app.monobank.corporate")
data class MonobankCorporateProperties(
    /** `keyId` from Monobank's registration status endpoint. */
    val keyId: String = "",
    /** The secp256k1 private key, as PEM text or as a path to a PEM file. */
    val privateKey: String = "",
    /**
     * How long a consent request stays useful to us.
     *
     * Monobank keeps it open for 24 hours; nobody leaves a dialog waiting that long, and
     * a shorter window is one fewer approved-but-forgotten request lying around.
     */
    val consentTimeout: Duration = Duration.ofMinutes(10)
) {
    val configured: Boolean get() = keyId.isNotBlank() && privateKey.isNotBlank()
}

@Configuration
@EnableConfigurationProperties(MonobankCorporateProperties::class)
class MonobankCorporateConfig

/**
 * True only when both halves of the key are actually set to something.
 *
 * `@ConditionalOnProperty` would not do: these are declared in `application.yml` with
 * empty defaults so that the settings are discoverable, and to that annotation an empty
 * string is a property that exists. The difference matters - it is between a deployment
 * that does not offer Monobank and one that offers it and fails.
 */
class MonobankCorporateConfigured : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        fun set(key: String) = !context.environment.getProperty("app.monobank.corporate.$key").isNullOrBlank()
        return set("key-id") && set("private-key")
    }
}

/** What [MonobankCorporateApi.requestAccess] hands back for the customer to approve. */
data class MonoTokenRequest(val tokenRequestId: String, val acceptUrl: String)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class MonoTokenRequestBody(
    val tokenRequestId: String = "",
    val acceptUrl: String = ""
)

/**
 * Monobank's corporate API - the same endpoints as the personal one, authenticated by
 * signature instead of by a token the customer had to fetch themselves.
 *
 * Three headers do the work. `X-Key-Id` names the company, `X-Time` stamps the request,
 * and `X-Sign` is an ECDSA signature over those parts concatenated with the request path -
 * `X-Time | X-Request-Id | path`, with the request id left out on the endpoints that have
 * no customer yet. Signing the path is what stops a captured signature from being replayed
 * against a different endpoint, and signing the time is what stops it being replayed at
 * all for long.
 *
 * `X-Request-Id` is the customer's consent: it is issued by [requestAccess], becomes usable
 * once they approve it in the Monobank app, and from then on stands in for the personal
 * token everywhere. It is the only per-customer secret this app stores for Monobank.
 *
 * Curve secp256k1, which the JDK dropped from SunEC in 16 - hence BouncyCastle.
 */
internal class MonobankCorporateApi(
    private val properties: MonobankCorporateProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    /** Parsed once: the PEM never changes, and parsing per request would be per statement. */
    private val privateKey: PrivateKey by lazy { loadPrivateKey(properties.privateKey) }

    // ── Endpoints ───────────────────────────────────────────────────────────

    /**
     * Starts a consent: Monobank mints a request id and a URL for the customer to approve.
     *
     * The URL is `https://mbnk.app/auth/...`, which opens the Monobank app on a phone and
     * has to be shown as a QR anywhere else.
     */
    fun requestAccess(): MonoTokenRequest {
        val body = send("POST", "/personal/auth/request", requestId = null)
        val parsed = objectMapper.readValue(body, MonoTokenRequestBody::class.java)
        if (parsed.tokenRequestId.isBlank() || parsed.acceptUrl.isBlank()) {
            throw BankRequestException("Monobank returned an unusable consent request")
        }
        return MonoTokenRequest(parsed.tokenRequestId, parsed.acceptUrl)
    }

    /**
     * Whether the customer has approved [requestId] yet.
     *
     * Monobank answers 401 until they do, which is a normal state here rather than a
     * failure - it is what the client is polling for. A 404 means the request expired or
     * was never ours, and that one *is* terminal.
     */
    fun isAccessGranted(requestId: String): Boolean {
        val response = raw("GET", "/personal/auth/request", requestId)
        return when (response.statusCode()) {
            200 -> true
            401 -> false
            404 -> throw BankAuthException("The Monobank request expired before it was approved")
            else -> throw failureFor(response)
        }
    }

    fun clientInfo(requestId: String): MonoClientInfo =
        objectMapper.readValue(
            send("GET", "/personal/client-info", requestId),
            MonoClientInfo::class.java
        )

    fun statement(requestId: String, account: String, fromEpoch: Long, toEpoch: Long): List<MonoStatement> {
        val body = send("GET", "/personal/statement/$account/$fromEpoch/$toEpoch", requestId)
        return objectMapper.readValue(
            body,
            objectMapper.typeFactory.constructCollectionType(List::class.java, MonoStatement::class.java)
        )
    }

    // ── Transport ───────────────────────────────────────────────────────────

    private fun send(method: String, path: String, requestId: String?): String {
        val response = raw(method, path, requestId)
        if (response.statusCode() == 200) return response.body()
        throw failureFor(response)
    }

    private fun raw(method: String, path: String, requestId: String?): HttpResponse<String> {
        val time = clock.instant().epochSecond.toString()
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("$MONO_API$path"))
            .timeout(Duration.ofSeconds(30))
            .header("X-Key-Id", properties.keyId)
            .header("X-Time", time)
            .header("X-Sign", sign(time, requestId, path))
        requestId?.let { builder.header("X-Request-Id", it) }
        // Every corporate endpoint this app uses takes its arguments in the path or the
        // headers; none of them has a body.
        builder.method(method, HttpRequest.BodyPublishers.noBody())

        return try {
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: java.io.IOException) {
            throw BankRequestException("Monobank is unreachable", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw BankRequestException("The request to Monobank was interrupted", e)
        }
    }

    private fun failureFor(response: HttpResponse<String>): RuntimeException {
        val description = errorDescription(response.body())
        return when (response.statusCode()) {
            401 -> BankAuthException(
                // 401 anywhere but the consent poll means the customer took the access
                // back, which they do from inside the Monobank app and we never hear about.
                description ?: "Monobank access was revoked - reconnect to grant it again"
            )
            403 -> {
                // Not the customer's problem: the company's key is wrong, unapproved, or
                // lacks the permission the endpoint needs.
                log.warn("Monobank rejected this deployment's key: {}", description)
                BankAuthException(description ?: "Monobank rejected this application's key")
            }
            429 -> BankRequestException(
                "Monobank is rate limiting requests - statements allow about one request a minute"
            )
            else -> BankRequestException(
                "Monobank error ${response.statusCode()}${description?.let { ": $it" } ?: ""}"
            )
        }
    }

    private fun errorDescription(body: String): String? = try {
        objectMapper.readTree(body)?.path("errorDescription")?.takeIf { it.isTextual }?.asText()
    } catch (_: Exception) {
        null
    }

    // ── Signing ─────────────────────────────────────────────────────────────

    /**
     * `base64(ECDSA-SHA256(X-Time | X-Request-Id | path))`, concatenated with no separator.
     *
     * The request id is simply absent from the string when the request has none, which is
     * what Monobank's own examples do - not an empty placeholder.
     */
    internal fun sign(time: String, requestId: String?, path: String): String {
        val payload = time + (requestId ?: "") + path
        val signature = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME).apply {
            initSign(privateKey)
            update(payload.toByteArray(StandardCharsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    companion object {
        init {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        /**
         * Reads the configured key, whether it was given as PEM text or as a file path.
         *
         * Both PEM shapes `openssl` produces are accepted. `openssl ecparam -genkey` writes
         * SEC1 ("BEGIN EC PRIVATE KEY"), which carries the curve but is not what
         * [PKCS8EncodedKeySpec] reads, so it is wrapped into PKCS#8 here rather than made
         * the operator's problem.
         */
        internal fun loadPrivateKey(configured: String): PrivateKey {
            val text = configured.trim().let {
                if (it.startsWith("-----BEGIN")) it else Files.readString(Path.of(it)).trim()
            }
            val sec1 = text.contains("BEGIN EC PRIVATE KEY")
            val der = Base64.getMimeDecoder().decode(
                text.lineSequence()
                    .filterNot { it.startsWith("-----") }
                    .joinToString("")
                    .trim()
            )
            val pkcs8 = if (sec1) {
                PrivateKeyInfo(
                    AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1),
                    ASN1Sequence.fromByteArray(der)
                ).encoded
            } else {
                der
            }
            return try {
                KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
                    .generatePrivate(PKCS8EncodedKeySpec(pkcs8))
            } catch (e: Exception) {
                throw IllegalStateException(
                    "app.monobank.corporate.private-key is not a readable secp256k1 PEM key", e
                )
            }
        }
    }
}
