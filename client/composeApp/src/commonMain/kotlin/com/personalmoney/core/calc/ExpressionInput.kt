package com.personalmoney.core.calc


/**
 * Cleans up what a keyboard hands over, so that only things [Calculator] can read reach the
 * amount field.
 *
 * This used to translate numpad presses into an expression. The numpad is gone - amounts are
 * typed on the system keyboard now - but the sanitising did not go with it: a `Decimal` IME
 * is a hint, not a guarantee. Predictive input, a pasted string and a hardware keyboard can
 * all put a letter or a second decimal point into the field, and `12.3.4` would otherwise
 * leave it permanently invalid with nothing on screen explaining why.
 *
 * Both `.` and `,` are kept as typed. IMEs in ru/uk locales emit a comma, and `Calculator`
 * already folds it onto a dot, so there is nothing to correct and no reason to overwrite what
 * the user actually pressed.
 */
object ExpressionInput {

    private const val OPERATORS = "+-*/\u2212\u00D7\u00F7"
    private const val SEPARATORS = ".,"

    fun sanitize(typed: String): String {
        val out = StringBuilder()
        var separatorInThisNumber = false
        for (char in typed) {
            when {
                char.isDigit() -> out.append(char)

                char in SEPARATORS -> if (!separatorInThisNumber) {
                    separatorInThisNumber = true
                    out.append(char)
                }

                // An operator ends the current number, so the next one may have its own
                // separator. Nothing on the system keypad produces these any more; they
                // survive a paste, and Calculator still evaluates them.
                char in OPERATORS -> {
                    separatorInThisNumber = false
                    out.append(char)
                }

                // Everything else is dropped rather than rejected, so a stray character
                // simply fails to appear instead of blocking the keystroke after it.
            }
        }
        return out.toString()
    }
}
