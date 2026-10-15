package com.personalmoney.core.auth

import com.personalmoney.core.network.InstanceDto

/**
 * Turns what a person types into the base URL every request is resolved against.
 *
 * A self-hosted server is a LAN address as often as a domain, so a bare host means https
 * (the safe default) and an explicit http:// is respected - with a warning in the UI.
 */
object ServerAddress {
    /** The `/api/instance` apiVersion range this build understands. */
    const val MIN_API_VERSION = 1
    const val MAX_API_VERSION = 1

    private const val HTTP = "http://"
    private const val HTTPS = "https://"

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val scheme = when {
            trimmed.startsWith(HTTP, ignoreCase = true) -> HTTP
            trimmed.startsWith(HTTPS, ignoreCase = true) -> HTTPS
            else -> null
        }
        // Slashes are trimmed after the scheme is split off: trimming first would turn a
        // lone "https://" into "https:", which then reads as a host called "https".
        val rest = (if (scheme != null) trimmed.substring(scheme.length) else trimmed).trimEnd('/')
        // Any other scheme ("ftp://host") leaves a "://" behind.
        if ("://" in rest) return null
        val host = rest.substringBefore('/').substringBefore(':')
        if (host.isEmpty()) return null
        return "${scheme ?: HTTPS}$rest/"
    }

    fun isCleartext(base: String): Boolean = base.startsWith(HTTP, ignoreCase = true)
}

/** What a typed server address turned out to be. */
sealed interface ServerCheck {
    data class Ok(val baseUrl: String, val instance: InstanceDto) : ServerCheck
    data object Invalid : ServerCheck
    data object Unreachable : ServerCheck
    data object NotPersonalMoney : ServerCheck
    data class Unsupported(val apiVersion: Int) : ServerCheck
}
