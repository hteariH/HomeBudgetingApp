package com.personalmoney.core.data

/**
 * The server stores colours as `"#RRGGBB"`; the shared models carry the ARGB value Compose
 * wants. Every REST repository that maps a coloured row needs both directions.
 */

internal fun String.parseColorOrDefault(default: Long = DEFAULT_COLOR): Long {
    val hex = removePrefix("#")
    if (hex.length != 6) return default
    val rgb = hex.toLongOrNull(16) ?: return default
    return 0xFF000000L or rgb
}

internal fun Long.toHexColor(): String {
    val rgb = this and 0xFFFFFFL
    return "#" + rgb.toString(16).padStart(6, '0')
}

internal const val DEFAULT_COLOR = 0xFF4F46E5L
