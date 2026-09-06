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

    @Test fun `a dollar stablecoin converts with no stored rate at all`() {
        // The whole bug, in one line: Frankfurter has no USDT, so an imported crypto
        // account converted to null and was dropped from the total in silence.
        val fx = FxSnapshot.from(emptyList())
        assertEquals(1.0, fx.rate("USDT", "USD")!!, 0.0001)
        assertEquals(1.0, fx.rate("USD", "USDC")!!, 0.0001)
    }

    @Test fun `a pegged coin reaches every currency the feed does carry`() {
        val fx = FxSnapshot.from(listOf(rate("EUR", "USD", 1.08)))
        // USDT -> USD -> EUR
        assertEquals(1.0 / 1.08, fx.rate("USDT", "EUR")!!, 0.0001)
    }

    @Test fun `a stored rate beats the peg`() {
        // A depeg is somebody's real problem; the app must not argue with them about it.
        val fx = FxSnapshot.from(listOf(rate("USDT", "USD", 0.93)))
        assertEquals(0.93, fx.rate("USDT", "USD")!!, 0.0001)
    }

    @Test fun `an unpegged crypto still needs a rate of its own`() {
        val fx = FxSnapshot.from(listOf(rate("EUR", "USD", 1.08)))
        assertNull(fx.rate("BTC", "EUR"))
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
