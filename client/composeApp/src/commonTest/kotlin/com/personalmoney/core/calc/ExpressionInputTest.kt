package com.personalmoney.core.calc

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the amount field against what a real keyboard can produce.
 *
 * A `KeyboardType.Decimal` field is a request, not a contract: predictive input, a paste and
 * a hardware keyboard all reach it, so these are the cases the old numpad made impossible by
 * construction and the system keyboard does not.
 */
class ExpressionInputTest {

    @Test
    fun `digits pass through`() {
        assertEquals("1234", ExpressionInput.sanitize("1234"))
    }

    @Test
    fun `a decimal point is kept`() {
        assertEquals("12.34", ExpressionInput.sanitize("12.34"))
    }

    @Test
    fun `a comma is kept as typed`() {
        // ru/uk IMEs emit a comma, and Calculator folds it onto a dot - so rewriting it here
        // would only make the field disagree with the key the user pressed.
        assertEquals("12,34", ExpressionInput.sanitize("12,34"))
    }

    @Test
    fun `a second separator in the same number is ignored`() {
        // The case that actually happens: the field holds "12.34" and the separator key is
        // pressed again. The keystroke does nothing, which is what the numpad did too.
        assertEquals("12.34", ExpressionInput.sanitize("12.34."))
        assertEquals("12.34", ExpressionInput.sanitize("12.34,"))
    }

    @Test
    fun `only the separator is dropped, not what follows it`() {
        // Reachable by pasting, or by typing into the middle of an existing amount. Dropping
        // the digits after it as well would throw away input the user can see they typed.
        assertEquals("12.345", ExpressionInput.sanitize("12.34.5"))
    }

    @Test
    fun `each number gets its own separator`() {
        assertEquals("1.5+2.5", ExpressionInput.sanitize("1.5+2.5"))
    }

    @Test
    fun `letters are dropped`() {
        // A predictive keyboard can autocomplete a word into the field.
        assertEquals("12", ExpressionInput.sanitize("1abc2"))
    }

    @Test
    fun `spaces and currency symbols are dropped`() {
        assertEquals("1249.90", ExpressionInput.sanitize("1 249.90 ₴"))
    }

    @Test
    fun `pasted operators survive`() {
        // Nothing on the Decimal keypad produces these, but a paste can - and Calculator
        // still evaluates them, so dropping them would silently change the amount.
        assertEquals("12+3−4×5÷6", ExpressionInput.sanitize("12+3−4×5÷6"))
    }

    @Test
    fun `a leading minus is kept`() {
        assertEquals("-5", ExpressionInput.sanitize("-5"))
    }

    @Test
    fun `empty stays empty`() {
        assertEquals("", ExpressionInput.sanitize(""))
    }
}
