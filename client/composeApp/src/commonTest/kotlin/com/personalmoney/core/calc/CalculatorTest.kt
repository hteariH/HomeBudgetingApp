package com.personalmoney.core.calc

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class CalculatorTest {

    @Test fun `evaluates plain number`() {
        assertEquals(BigDecimal.parseString("12.34"), Calculator.evaluate("12.34", 2))
    }

    @Test fun `evaluates simple addition`() {
        assertEquals(BigDecimal.parseString("15.00"), Calculator.evaluate("10+5", 2))
    }

    @Test fun `evaluates subtraction`() {
        assertEquals(BigDecimal.parseString("4.50"), Calculator.evaluate("10-5.5", 2))
    }

    @Test fun `respects operator precedence`() {
        assertEquals(BigDecimal.parseString("16.00"), Calculator.evaluate("10+2*3", 2))
    }

    @Test fun `evaluates division with rounding`() {
        assertEquals(BigDecimal.parseString("3.33"), Calculator.evaluate("10/3", 2))
    }

    @Test fun `unary minus is allowed`() {
        assertEquals(BigDecimal.parseString("-5.00"), Calculator.evaluate("-5", 2))
    }

    @Test fun `accepts unicode operators`() {
        assertEquals(BigDecimal.parseString("6.00"), Calculator.evaluate("12÷2", 2))
        assertEquals(BigDecimal.parseString("24.00"), Calculator.evaluate("12×2", 2))
        assertEquals(BigDecimal.parseString("10.00"), Calculator.evaluate("12−2", 2))
    }

    @Test fun `comma as decimal separator works`() {
        assertEquals(BigDecimal.parseString("12.34"), Calculator.evaluate("12,34", 2))
    }

    @Test fun `trailing operator is dropped`() {
        assertEquals(BigDecimal.parseString("12.00"), Calculator.evaluate("12+", 2))
    }

    @Test fun `division by zero returns null`() {
        assertNull(Calculator.evaluate("10/0", 2))
    }

    @Test fun `multiple decimal points return null`() {
        assertNull(Calculator.evaluate("12.3.4", 2))
    }

    @Test fun `empty expression returns null`() {
        assertNull(Calculator.evaluate("", 2))
    }

    @Test fun `hasOperator detects operators`() {
        assertEquals(true, Calculator.hasOperator("10+5"))
        assertEquals(true, Calculator.hasOperator("10×2"))
        assertEquals(false, Calculator.hasOperator("123"))
        assertEquals(false, Calculator.hasOperator("-123"))
    }

    @Test fun `decimal scale respects passed decimals`() {
        assertEquals(BigDecimal.parseString("10"), Calculator.evaluate("10", 0))
        assertEquals(BigDecimal.parseString("10.000"), Calculator.evaluate("10", 3))
    }
}
