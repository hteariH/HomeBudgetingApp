package com.personalmoney.core.currency

import com.personalmoney.core.database.entity.ExchangeRateEntity
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.ExchangeRateSource
import com.personalmoney.core.domain.Money
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FxSnapshotTest {

    private fun rate(base: String, quote: String, rate: Double) = ExchangeRateEntity(
        baseCode = base, quoteCode = quote, rate = rate,
        date = LocalDate(2026, 5, 1),
        source = ExchangeRateSource.MANUAL,
        updatedAtEpochMs = 0
    )

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
