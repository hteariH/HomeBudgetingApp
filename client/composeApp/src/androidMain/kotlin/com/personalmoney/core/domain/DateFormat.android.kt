package com.personalmoney.core.domain

import kotlinx.datetime.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The pattern the screens used before this became shared code, unchanged.
 *
 * Rebuilt per call rather than held in a val: DateTimeFormatter captures the locale it was
 * built with, and the app's language can change under it while the process lives.
 */
actual fun formatMediumDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        .format(java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth))
