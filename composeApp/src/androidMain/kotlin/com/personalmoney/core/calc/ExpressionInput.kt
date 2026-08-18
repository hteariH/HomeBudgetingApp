package com.personalmoney.core.calc

import com.personalmoney.core.ui.components.NumpadKey

/**
 * Pure numpad-key-to-expression appender shared by the quick-add and transfer flows so that
 * calculator-style amount entry behaves identically everywhere.
 *
 * Handles digit / dot / operator keys. BACKSPACE and DONE are no-ops here — callers handle
 * those (drop-last and save, respectively) before delegating to this function.
 */
object ExpressionInput {

    fun append(current: String, key: NumpadKey): String {
        val symbol = when (key) {
            NumpadKey.PLUS -> "+"
            NumpadKey.MINUS -> "−"
            NumpadKey.MULTIPLY -> "×"
            NumpadKey.DIVIDE -> "÷"
            NumpadKey.DOT -> "."
            NumpadKey.DIGIT_0 -> "0"
            NumpadKey.DIGIT_1 -> "1"
            NumpadKey.DIGIT_2 -> "2"
            NumpadKey.DIGIT_3 -> "3"
            NumpadKey.DIGIT_4 -> "4"
            NumpadKey.DIGIT_5 -> "5"
            NumpadKey.DIGIT_6 -> "6"
            NumpadKey.DIGIT_7 -> "7"
            NumpadKey.DIGIT_8 -> "8"
            NumpadKey.DIGIT_9 -> "9"
            else -> ""
        }

        // Sanitize: don't allow leading operator (except minus), no double operators, no double dot in current number
        return when {
            symbol.isEmpty() -> current
            symbol == "." -> {
                val lastNumberPart = current.takeLastWhile { it.isDigit() || it == '.' }
                if (lastNumberPart.contains('.')) current
                else if (current.isEmpty() || current.last() in "+−×÷") current + "0."
                else current + "."
            }
            symbol in setOf("+", "−", "×", "÷") -> {
                if (current.isEmpty()) {
                    if (symbol == "−") symbol else current
                } else if (current.last() in "+−×÷") {
                    current.dropLast(1) + symbol
                } else if (current.last() == '.') {
                    current
                } else {
                    current + symbol
                }
            }
            else -> current + symbol
        }
    }
}
