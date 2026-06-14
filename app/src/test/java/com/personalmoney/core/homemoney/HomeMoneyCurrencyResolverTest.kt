package com.personalmoney.core.homemoney

import com.personalmoney.core.database.seed.DefaultCurrencies
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMoneyCurrencyResolverTest {

    private val resolver = HomeMoneyCurrencyResolver(DefaultCurrencies.list)

    @Test
    fun `resolves HomeMoney symbols to ISO codes`() {
        assertEquals("USD", resolver.resolve("$"))   // shared symbol -> explicit override
        assertEquals("EUR", resolver.resolve("€"))   // unambiguous symbol match
        assertEquals("UAH", resolver.resolve("₴"))
        assertEquals("HUF", resolver.resolve("ft"))  // Forint abbreviation -> "Ft" symbol
        assertEquals("PLN", resolver.resolve("zł"))
    }

    @Test
    fun `passes through real ISO and custom codes`() {
        assertEquals("USD", resolver.resolve("USD"))
        assertEquals("uah".uppercase(), resolver.resolve("uah"))
        assertEquals("BTC", resolver.resolve("BTC")) // seeded custom code
    }

    @Test
    fun `unknown shortname is kept as an upper-cased custom code`() {
        assertEquals("XYZ", resolver.resolve("xyz"))
    }
}
