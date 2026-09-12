package com.homebudget.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Conversion between the integer minor units the database stores and the decimals
 * the HTTP API speaks.
 *
 * The exponent is per currency, not a constant 2 - see [Currency.decimals]. Getting
 * this wrong is silent and expensive: a JPY amount scaled as if it had two decimals
 * is off by a factor of 100.
 */
object MinorUnits {

    /**
     * @throws IllegalArgumentException if the amount cannot be represented, rather
     *   than truncating. A wrong balance is worse than a rejected request.
     */
    fun toMinor(amount: BigDecimal, decimals: Int): Long {
        require(decimals in 0..18) { "unsupported currency scale: $decimals" }
        val scaled = amount.movePointRight(decimals).setScale(0, RoundingMode.HALF_UP)
        return try {
            scaled.longValueExact()
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("amount $amount is out of range for this currency", e)
        }
    }

    fun toDecimal(minor: Long, decimals: Int): BigDecimal {
        require(decimals in 0..18) { "unsupported currency scale: $decimals" }
        return BigDecimal.valueOf(minor).movePointLeft(decimals).setScale(decimals)
    }

    fun rescale(amountInHundredths: Long, decimals: Int): Long = when {
        decimals == 2 -> amountInHundredths
        decimals > 2 -> amountInHundredths * pow10(decimals - 2)
        else -> Math.round(amountInHundredths.toDouble() / pow10(2 - decimals))
    }

    private fun pow10(n: Int): Long {
        var result = 1L
        repeat(n) { result *= 10 }
        return result
    }
}
