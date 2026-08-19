package com.personalmoney.core.domain

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals


class MoneyTest {

    private val usd = CurrencyCode("USD")

    @Test fun `times normal case rounds half up`() {
        val m = Money(100, usd) // 1.00
        assertEquals(250L, (m * BigDecimal.parseString("2.5")).minor)
    }

    @Test fun `times clamps to Long MAX on overflow instead of flipping sign`() {
        val big = Money(Long.MAX_VALUE / 2, usd)
        assertEquals(Long.MAX_VALUE, (big * BigDecimal.fromInt(10)).minor)
    }

    @Test fun `times clamps to Long MIN on negative overflow`() {
        val big = Money(Long.MIN_VALUE / 2, usd)
        assertEquals(Long.MIN_VALUE, (big * BigDecimal.fromInt(10)).minor)
    }

    @Test fun `unaryMinus of Long MIN does not overflow to itself`() {
        assertEquals(Long.MAX_VALUE, (-Money(Long.MIN_VALUE, usd)).minor)
    }

    @Test fun `unaryMinus normal case`() {
        assertEquals(-500L, (-Money(500, usd)).minor)
    }

    @Test fun `fromDecimal clamps a value beyond Long range`() {
        assertEquals(Long.MAX_VALUE, Money.fromDecimal(BigDecimal.parseString("1E30"), usd).minor)
    }
}
