package com.personalmoney.core.domain

@JsFun("""(m) => new Intl.DateTimeFormat(undefined, { month: 'short' })
    .format(new Date(Date.UTC(2000, m - 1, 1)))""")
private external fun intlShortMonth(monthNumber: Int): String

/**
 * `Intl.DateTimeFormat` with no locale argument, so the browser's own language decides -
 * the same source Compose Resources reads for which strings.xml to serve.
 *
 * The year is arbitrary; only the month is formatted. UTC so a timezone west of the line
 * cannot roll the date back into the previous month.
 */
actual fun shortMonthName(monthNumber: Int): String = intlShortMonth(monthNumber)
