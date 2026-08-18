package com.personalmoney.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class AppendToExpressionTest {

    @Test
    fun `digit appends to empty expression`() {
        assertEquals("5", appendToExpression("", "5"))
    }

    @Test
    fun `multiple digits build a number`() {
        var expr = ""
        for (d in listOf("1", "2", "3")) expr = appendToExpression(expr, d)
        assertEquals("123", expr)
    }

    @Test
    fun `leading zero is replaced by non-zero digit`() {
        assertEquals("7", appendToExpression("0", "7"))
    }

    @Test
    fun `zero after zero stays zero`() {
        assertEquals("0", appendToExpression("0", "0"))
    }

    @Test
    fun `dot on empty expression prepends 0`() {
        assertEquals("0.", appendToExpression("", "."))
    }

    @Test
    fun `dot appends to existing integer`() {
        assertEquals("42.", appendToExpression("42", "."))
    }

    @Test
    fun `second dot is ignored`() {
        assertEquals("3.14", appendToExpression("3.14", "."))
    }

    @Test
    fun `zero appended after decimal`() {
        assertEquals("1.0", appendToExpression("1.", "0"))
    }

    @Test
    fun `zero appended after decimal digits`() {
        assertEquals("1.50", appendToExpression("1.5", "0"))
    }

    @Test
    fun `full number constructed correctly`() {
        var expr = ""
        for (d in listOf("1", "0", "0", ".", "5")) expr = appendToExpression(expr, d)
        assertEquals("100.5", expr)
    }

    @Test
    fun `digit after zero on leading zero`() {
        assertEquals("9", appendToExpression("0", "9"))
    }
}
