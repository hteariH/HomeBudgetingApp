package com.personalmoney.core.calc

/**
 * What a numpad press means, independent of how the numpad is drawn.
 *
 * Lives next to [ExpressionInput] rather than beside the Compose numpad because it is the
 * model, not the rendering: the expression logic is shared by every platform, while the
 * keypad itself is a UI concern.
 */
enum class NumpadKey(val display: String) {
    DIGIT_0("0"), DIGIT_1("1"), DIGIT_2("2"), DIGIT_3("3"), DIGIT_4("4"),
    DIGIT_5("5"), DIGIT_6("6"), DIGIT_7("7"), DIGIT_8("8"), DIGIT_9("9"),
    DOT("."), PLUS("+"), MINUS("−"), MULTIPLY("×"), DIVIDE("÷"),
    BACKSPACE("⌫"), DONE("✓")
}
