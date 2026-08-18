package com.personalmoney.core.domain

/**
 * Money as the user's locale writes it.
 *
 * expect/actual rather than one shared implementation because locale-aware number
 * formatting is exactly what each platform already does well and differently: ICU on
 * Android, Intl in a browser. Hand-rolling it would mean picking group and decimal
 * separators ourselves and getting them wrong for someone.
 *
 * There is no locale parameter. Every call site used the platform default, and passing one
 * through three platforms buys nothing until the app actually lets a user override it.
 */
expect object MoneyFormatter {
    /** With the currency symbol, e.g. "$ 1,250.75". */
    fun format(money: Money): String

    /** Digits only, e.g. "1,250.75" - for inputs and places where the currency is implied. */
    fun formatPlain(money: Money): String
}
