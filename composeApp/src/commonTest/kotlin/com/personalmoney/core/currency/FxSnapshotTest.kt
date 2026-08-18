package com.personalmoney.core.currency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money

class FxSnapshotTest {

    private fun rate(base: String, quote: String, rate: Double) = FxRate(base, quote, rate)

    @Test fun `same currency returns 1`() {
        val fx = FxSnapshot.from(emptyList())
        assertEquals(1.0, fx.rate("USD", "USD")!!, 0.0001)
    }

    @Test fun `direct rate`() {
        val fx = FxSnapshot.from(listOf(rate("USD", "EUR", 0.9)))
        assertEquals(0.9, fx.rate("USD", "EUR")!!, 0.0001)
    }

    @Test fun `inverse rate`() {
        val fx = FxSnapshot.from(listOf(rate("USD", "EUR", 0.9)))
        // 1 EUR = 1/0.9 USD ≈ 1.111
        assertEquals(1.111, fx.rate("EUR", "USD")!!, 0.001)
    }

    @Test fun `pivot via USD`() {
        val fx = FxSnapshot.from(listOf(
            rate("USD", "EUR", 0.9),
            rate("USD", "GBP", 0.8)
        ))
        // EUR -> USD -> GBP
        val expected = (1.0 / 0.9) * 0.8
        assertEquals(expected, fx.rate("EUR", "GBP")!!, 0.001)
    }

    @Test fun `unknown currencies return null`() {
        val fx = FxSnapshot.from(emptyList())
        assertNull(fx.rate("XXX", "EUR"))
    }

    @Test fun `zero stored rate is ignored, not inverted to infinity`() {
        val fx = FxSnapshot.from(listOf(rate("USD", "EUR", 0.0)))
        assertNull(fx.rate("USD", "EUR")) // direct 0 rejected
        assertNull(fx.rate("EUR", "USD")) // inverse would be 1/0 = Infinity → rejected
    }

    @Test fun `non-finite stored rate is ignored`() {
        val fx = FxSnapshot.from(listOf(rate("USD", "EUR", Double.NaN)))
        assertNull(fx.rate("USD", "EUR"))
        assertNull(fx.rate("EUR", "USD"))
    }

    @Test fun `negative stored rate is ignored`() {
        val fx = FxSnapshot.from(listOf(rate("USD", "EUR", -0.9)))
        assertNull(fx.rate("USD", "EUR"))
    }

    @Test fun `convert respects target decimals`() {
        val fx = FxSnapshot.from(listOf(rate("USD", "JPY", 150.0)))
        // 1 USD = 150 JPY (0 decimals)
        val money = Money(100, CurrencyCode("USD")) // 1.00 USD
        val result = fx.convert(money, CurrencyCode("JPY"))
        assertNotNull(result)
        // 1.00 * 150 = 150 JPY → minor = 150 (0 decimals)
        assertEquals(150L, result!!.minor)
    }
}
