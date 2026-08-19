package com.personalmoney.core.domain

import kotlinx.datetime.LocalDate

/**
 * A date as a person in this locale writes it: "3 Feb 2026", "3 февр. 2026 г.".
 *
 * expect/actual for the same reason as [shortMonthName] - the platform owns the pattern
 * and the month abbreviations for every locale, and reimplementing that would mean owning
 * a table the app has no business owning. Android keeps java.time, so what existing users
 * see does not move.
 */
expect fun formatMediumDate(date: LocalDate): String
