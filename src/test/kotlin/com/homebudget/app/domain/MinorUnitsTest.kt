package com.homebudget.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class MinorUnitsTest {

    @Test
    fun `scales by the currency's own exponent, not a constant two`() {
        // The bug this guards against: treating every currency as 2-decimal makes a
        // JPY amount 100x too small and a BTC amount 1,000,000x too small.
        assertEquals(1234L, MinorUnits.toMinor(BigDecimal("12.34"), 2))
        assertEquals(12L, MinorUnits.toMinor(BigDecimal("12"), 0))
        assertEquals(12340L, MinorUnits.toMinor(BigDecimal("12.34"), 3))
        assertEquals(100_000_000L, MinorUnits.toMinor(BigDecimal("1.00000000"), 8))
    }

    @Test
    fun `round trips through decimal and back`() {
        val cases = listOf(
            BigDecimal("0.00") to 2,
            BigDecimal("1999.99") to 2,
            BigDecimal("-45.50") to 2,
            BigDecimal("7") to 0,
            BigDecimal("0.001") to 3,
            BigDecimal("0.00000001") to 8
        )
        for ((amount, decimals) in cases) {
            val minor = MinorUnits.toMinor(amount, decimals)
            assertEquals(
                amount.setScale(decimals),
                MinorUnits.toDecimal(minor, decimals),
                "round trip failed for $amount at scale $decimals"
            )
        }
    }

    @Test
    fun `rounds half up rather than truncating`() {
        assertEquals(1235L, MinorUnits.toMinor(BigDecimal("12.345"), 2))
        assertEquals(1234L, MinorUnits.toMinor(BigDecimal("12.344"), 2))
        // Negative amounts round away from zero at the midpoint, consistently.
        assertEquals(-1235L, MinorUnits.toMinor(BigDecimal("-12.345"), 2))
    }

    @Test
    fun `rejects amounts that would silently truncate`() {
        // toLong() would have quietly wrapped; a wrong balance is worse than a 400.
        val tooBig = BigDecimal("100000000000000000000")
        assertThrows<IllegalArgumentException> { MinorUnits.toMinor(tooBig, 2) }
    }

    @Test
    fun `rejects an implausible currency scale`() {
        assertThrows<IllegalArgumentException> { MinorUnits.toMinor(BigDecimal.ONE, 19) }
        assertThrows<IllegalArgumentException> { MinorUnits.toDecimal(1L, -1) }
    }
}
