package com.personalmoney.core.calc

import com.personalmoney.core.ui.components.NumpadKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression guard for the shared numpad-expression sanitizer used by quick-add and transfer. */
class ExpressionInputTest {

    private fun append(start: String, vararg keys: NumpadKey): String {
        var s = start
        for (k in keys) s = ExpressionInput.append(s, k)
        return s
    }

    @Test
    fun `digits append in order`() {
        assertEquals("123", append("", NumpadKey.DIGIT_1, NumpadKey.DIGIT_2, NumpadKey.DIGIT_3))
    }

    @Test
    fun `leading minus is allowed`() {
        assertEquals("−", ExpressionInput.append("", NumpadKey.MINUS))
    }

    @Test
    fun `leading plus is rejected`() {
        assertEquals("", ExpressionInput.append("", NumpadKey.PLUS))
    }

    @Test
    fun `consecutive operators replace the previous one`() {
        assertEquals("5×", append("5", NumpadKey.PLUS, NumpadKey.MULTIPLY))
    }

    @Test
    fun `dot right after an operator yields a leading zero`() {
        assertEquals("5+0.", append("5", NumpadKey.PLUS, NumpadKey.DOT))
    }

    @Test
    fun `dot on empty yields a leading zero`() {
        assertEquals("0.", ExpressionInput.append("", NumpadKey.DOT))
    }

    @Test
    fun `second dot in the same number is rejected`() {
        assertEquals("1.5", append("1.5", NumpadKey.DOT))
    }

    @Test
    fun `backspace and done are no-ops in append`() {
        assertEquals("12", ExpressionInput.append("12", NumpadKey.BACKSPACE))
        assertEquals("12", ExpressionInput.append("12", NumpadKey.DONE))
    }
}
