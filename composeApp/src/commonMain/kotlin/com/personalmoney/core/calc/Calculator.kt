package com.personalmoney.core.calc

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode

/**
 * Stateless evaluator for amount-input expressions:
 *   digits, decimal point, + − × ÷ (also supports - and / as aliases)
 *
 * Two passes: tokenize → evaluate with operator precedence.
 * Returns null on parse failure or division by zero.
 */
object Calculator {
    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token
        data class Op(val symbol: Char) : Token
    }

    fun evaluate(expression: String, decimals: Int): BigDecimal? {
        val tokens = tokenize(expression) ?: return null
        if (tokens.isEmpty()) return null
        val firstPass = applyMulDiv(tokens) ?: return null
        val result = applyAddSub(firstPass) ?: return null
        return result.roundToDigitPositionAfterDecimalPoint(
            decimals.coerceAtLeast(0).toLong(),
            RoundingMode.ROUND_HALF_AWAY_FROM_ZERO,
        )
    }

    private fun tokenize(expr: String): List<Token>? {
        val normalized = expr
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace(',', '.')
            .replace(" ", "")
        if (normalized.isEmpty()) return emptyList()

        val tokens = mutableListOf<Token>()
        var i = 0
        var prevWasOp = true
        while (i < normalized.length) {
            val ch = normalized[i]
            when {
                ch.isDigit() || ch == '.' -> {
                    var j = i
                    var hasDot = false
                    while (j < normalized.length && (normalized[j].isDigit() || normalized[j] == '.')) {
                        if (normalized[j] == '.') {
                            if (hasDot) return null
                            hasDot = true
                        }
                        j++
                    }
                    val numStr = normalized.substring(i, j)
                    val value = runCatching { BigDecimal.parseString(numStr) }.getOrNull() ?: return null
                    tokens.add(Token.Num(value))
                    i = j
                    prevWasOp = false
                }
                ch == '+' || ch == '-' || ch == '*' || ch == '/' -> {
                    if (prevWasOp && ch == '-') {
                        // unary minus → emit 0 first
                        tokens.add(Token.Num(BigDecimal.ZERO))
                    } else if (prevWasOp) {
                        return null
                    }
                    tokens.add(Token.Op(ch))
                    prevWasOp = true
                    i++
                }
                else -> return null
            }
        }
        // trailing operator is allowed when partially typed — drop it
        if (tokens.isNotEmpty() && tokens.last() is Token.Op) {
            tokens.removeAt(tokens.lastIndex)
        }
        return tokens.takeIf { it.isNotEmpty() }
    }

    private fun applyMulDiv(tokens: List<Token>): List<Token>? {
        val result = mutableListOf<Token>()
        var i = 0
        while (i < tokens.size) {
            val tk = tokens[i]
            if (tk is Token.Op && (tk.symbol == '*' || tk.symbol == '/')) {
                if (result.isEmpty() || i + 1 >= tokens.size) return null
                val left = result.removeAt(result.lastIndex) as? Token.Num ?: return null
                val right = tokens[i + 1] as? Token.Num ?: return null
                val v = when (tk.symbol) {
                    '*' -> left.value * right.value
                    '/' -> {
                        if (right.value.compareTo(BigDecimal.ZERO) == 0) return null
                        left.value.divide(
                            right.value,
                            DecimalMode(30, RoundingMode.ROUND_HALF_AWAY_FROM_ZERO, 12),
                        )
                    }
                    else -> return null
                }
                result.add(Token.Num(v))
                i += 2
            } else {
                result.add(tk)
                i++
            }
        }
        return result
    }

    private fun applyAddSub(tokens: List<Token>): BigDecimal? {
        if (tokens.isEmpty()) return null
        var acc = (tokens[0] as? Token.Num)?.value ?: return null
        var i = 1
        while (i < tokens.size) {
            val op = (tokens[i] as? Token.Op)?.symbol ?: return null
            val right = (tokens.getOrNull(i + 1) as? Token.Num)?.value ?: return null
            acc = when (op) {
                '+' -> acc + right
                '-' -> acc - right
                else -> return null
            }
            i += 2
        }
        return acc
    }

    /** True if expression contains any of + − × ÷ (other than a leading unary minus). */
    fun hasOperator(expression: String): Boolean {
        val trimmed = expression.replace(" ", "").let {
            if (it.startsWith('-') || it.startsWith('−')) it.drop(1) else it
        }
        return trimmed.any { it in "+-*/×÷−" }
    }
}
