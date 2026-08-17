package com.homebudget.app.security

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts secrets that have to be stored retrievably - currently Monobank API tokens.
 *
 * These are not our secrets: a Monobank token reads somebody's real bank statement, and
 * on a public site the table holds other people's. Passwords are hashed because we never
 * need them back; these we do, so they are encrypted instead.
 *
 * AES-GCM with a random 12-byte IV per value, stored as `iv:ciphertext` in Base64.
 */
@Component
@Converter
class SecretCrypto(
    @Value("\${app.crypto.secret:}") private val configuredSecret: String,
    @Value("\${app.jwt.secret:}") private val jwtSecretFallback: String
) : AttributeConverter<String?, String?> {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    private val key: SecretKeySpec by lazy {
        val material = configuredSecret.ifBlank {
            // Falling back keeps a deployment that has not set app.crypto.secret working
            // rather than failing to boot, but the two secrets then rotate together.
            log.warn("app.crypto.secret is not set; deriving the storage key from app.jwt.secret")
            jwtSecretFallback
        }
        require(material.isNotBlank()) { "Neither app.crypto.secret nor app.jwt.secret is set" }
        SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(material.toByteArray()), "AES")
    }

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val encrypted = cipher.doFinal(attribute.toByteArray())
        return "${b64(iv)}:${b64(encrypted)}"
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        val parts = dbData.split(':', limit = 2)
        if (parts.size != 2) {
            // Written before this converter existed. Returned as-is so the row stays
            // usable; it re-encrypts the next time the entity is saved.
            return dbData
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, unB64(parts[0])))
            }
            String(cipher.doFinal(unB64(parts[1])))
        } catch (e: Exception) {
            // A key change makes old values unreadable. Failing the whole read would
            // take the account list down with it; the link simply stops working.
            log.warn("Could not decrypt a stored secret: {}", e.message)
            null
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun unB64(value: String) = Base64.getDecoder().decode(value)

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
