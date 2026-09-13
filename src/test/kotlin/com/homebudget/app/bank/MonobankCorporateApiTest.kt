package com.homebudget.app.bank

import com.fasterxml.jackson.databind.ObjectMapper
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64

/**
 * The signing scheme, which is the one part of the corporate API that cannot be checked by
 * reading it.
 *
 * Everything else in this integration fails loudly - a wrong path is a 404, a wrong field
 * is a null. A signature that is computed over the wrong bytes, or from a key that was
 * parsed into the wrong curve, fails as a flat 403 from Monobank with no hint as to which
 * of the two it was. So the bytes are pinned here, against a key this test generates
 * itself rather than one committed to the repository.
 */
class MonobankCorporateApiTest {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
        .apply { initialize(ECNamedCurveGenParameterSpec("secp256k1")) }
        .generateKeyPair()

    /** What `openssl pkcs8 -topk8` writes. */
    private val pkcs8Pem = pem("PRIVATE KEY", keyPair.private.encoded)

    /** What `openssl ecparam -genkey` writes, which is the likelier of the two to arrive. */
    private val sec1Pem = pem(
        "EC PRIVATE KEY",
        PrivateKeyInfo.getInstance(keyPair.private.encoded).parsePrivateKey().toASN1Primitive().encoded
    )

    @Test
    fun `signs the time, the request id and the path, concatenated`() {
        val api = apiWith(pkcs8Pem)
        val signature = api.sign("1554466347", "uLkwh3NzFAfEkj7urj5C7AU_", "/personal/client-info")

        assertTrue(
            verifies("1554466347uLkwh3NzFAfEkj7urj5C7AU_/personal/client-info", signature),
            "the signed payload is X-Time, X-Request-Id and the path with nothing between them"
        )
    }

    @Test
    fun `leaves the request id out entirely when there is none`() {
        val api = apiWith(pkcs8Pem)
        val signature = api.sign("1554466347", null, "/personal/auth/request")

        // Not an empty placeholder: the consent endpoints sign time and path alone, and a
        // separator would be signing something Monobank is not.
        assertTrue(verifies("1554466347/personal/auth/request", signature))
    }

    @Test
    fun `reads the PEM shape openssl ecparam produces`() {
        val signature = apiWith(sec1Pem).sign("1", null, "/x")

        // The curve is not in the PKCS#8 wrapper this has to be given; a parse that got it
        // wrong would still produce a signature, just not one Monobank can check.
        assertTrue(verifies("1/x", signature))
    }

    @Test
    fun `reads the key from a file when the setting is a path`() {
        val file = Files.createTempFile("mono-test", ".pem")
        Files.writeString(file, sec1Pem)
        try {
            assertTrue(verifies("1/x", apiWith(file.toString()).sign("1", null, "/x")))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `refuses a key it cannot read rather than signing with nothing`() {
        assertThrows<IllegalStateException> {
            MonobankCorporateApi.loadPrivateKey("-----BEGIN PRIVATE KEY-----\nbm90IGEga2V5\n-----END PRIVATE KEY-----")
        }
    }

    @Test
    fun `a statement window covers the whole of the closing day`() {
        val (from, to) = monoStatementWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1))

        assertEquals(LocalDate.of(2026, 3, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond(), from)
        assertEquals(from + 86_400, to)
    }

    @Test
    fun `a window wider than Monobank allows is refused before the request is spent`() {
        // Asking anyway costs the minute of rate limit that the next attempt needs.
        assertThrows<BankRequestException> {
            monoStatementWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1))
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun apiWith(privateKey: String) = MonobankCorporateApi(
        MonobankCorporateProperties(keyId = "test-key", privateKey = privateKey),
        ObjectMapper(),
        Clock.fixed(Instant.ofEpochSecond(1554466347), ZoneOffset.UTC)
    )

    private fun verifies(payload: String, signature: String): Boolean =
        Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME).run {
            initVerify(keyPair.public)
            update(payload.toByteArray(StandardCharsets.UTF_8))
            verify(Base64.getDecoder().decode(signature))
        }

    private fun pem(label: String, der: ByteArray): String = buildString {
        append("-----BEGIN $label-----\n")
        append(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der))
        append("\n-----END $label-----\n")
    }
}
