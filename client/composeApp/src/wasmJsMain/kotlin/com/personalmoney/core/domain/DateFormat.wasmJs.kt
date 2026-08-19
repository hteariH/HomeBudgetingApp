package com.personalmoney.core.domain

import kotlinx.datetime.LocalDate

@JsFun("""(y, m, d) => new Intl.DateTimeFormat(undefined,
    { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(Date.UTC(y, m - 1, d)))""")
private external fun intlMediumDate(year: Int, month: Int, day: Int): String

/**
 * `Intl.DateTimeFormat`, so the browser's language picks the pattern - a US reader gets
 * "Feb 3, 2026" and a Ukrainian one "3 лют. 2026 р.", which is the point.
 *
 * Built in UTC and read back in UTC: the date has no time or zone, and letting the local
 * zone in would shift it by a day for anyone west of Greenwich.
 */
actual fun formatMediumDate(date: LocalDate): String =
    intlMediumDate(date.year, date.monthNumber, date.dayOfMonth)
