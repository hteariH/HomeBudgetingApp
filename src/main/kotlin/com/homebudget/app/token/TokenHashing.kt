package com.homebudget.app.token

import java.security.MessageDigest
import java.security.SecureRandom

private val random = SecureRandom()
private const val BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

/** How many random characters follow the prefix: 40 base62 characters are 238 bits. */
const val TOKEN_RANDOM_LENGTH = 40

/** [prefix] followed by [TOKEN_RANDOM_LENGTH] random base62 characters. */
fun newToken(prefix: String): String = buildString(prefix.length + TOKEN_RANDOM_LENGTH) {
    append(prefix)
    repeat(TOKEN_RANDOM_LENGTH) { append(BASE62[random.nextInt(BASE62.length)]) }
}

/** Lower-case hex SHA-256 of [s] as UTF-8: the only form a token is stored in. */
fun sha256Hex(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
