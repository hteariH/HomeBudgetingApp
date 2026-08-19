package com.personalmoney.core.domain

/**
 * The short month name in the user's language: Jan / янв. / січ.
 *
 * expect/actual rather than twelve keys in the string table, for the same reason
 * [MoneyFormatter] is: the platform already knows every locale's month names and its
 * abbreviation rules, and hand-writing them would mean owning that data - and getting
 * Ukrainian's genitive forms right - for no gain. Android keeps using java.time, which is
 * what it does today, so nothing shifts under existing users.
 *
 * [monthNumber] is 1-based, as in `kotlinx.datetime`.
 */
expect fun shortMonthName(monthNumber: Int): String
