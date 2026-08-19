package com.personalmoney.core.domain

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * java.time's own month names, in the default locale - which AppCompat's per-app locale
 * setting drives, so the analytics axis follows the in-app language picker.
 */
actual fun shortMonthName(monthNumber: Int): String =
    Month.of(monthNumber).getDisplayName(TextStyle.SHORT, Locale.getDefault())
