package com.personalmoney.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The amount field on a real device, without the rest of the app.
 *
 * The screens that host it sit behind sign-in, so this is what exercises the component the
 * numpad was replaced with: that it renders, that typing reaches the caller, and that a value
 * set from elsewhere - a scanned receipt - shows up rather than being held off by the field's
 * own copy of the text.
 */
class AmountFieldTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsAZeroPlaceholderWhenEmpty() {
        compose.setContent {
            AmountField(
                expression = "",
                onExpressionChange = {},
                currencySymbol = "₴",
                previewResult = null,
            )
        }

        compose.onNodeWithText("₴").assertIsDisplayed()
        compose.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun typingReachesTheCaller() {
        var expression by mutableStateOf("")
        compose.setContent {
            AmountField(
                expression = expression,
                onExpressionChange = { expression = it },
                currencySymbol = "₴",
                previewResult = null,
            )
        }

        compose.onNode(hasSetTextAction()).performTextInput("249")
        compose.waitForIdle()

        assertEquals("249", expression)
        compose.onNodeWithText("249").assertIsDisplayed()
    }

    @Test
    fun adoptsAValueSetFromOutside() {
        // What a receipt scan does: the view model's expression changes while the field is
        // already on screen, and the field has to follow rather than keep its own copy.
        var expression by mutableStateOf("12")
        compose.setContent {
            AmountField(
                expression = expression,
                onExpressionChange = { expression = it },
                currencySymbol = "₴",
                previewResult = null,
            )
        }

        compose.onNodeWithText("12").assertIsDisplayed()
        expression = "91.90"
        compose.waitForIdle()

        compose.onNodeWithText("91.90").assertIsDisplayed()
    }

    @Test
    fun showsThePreviewLineForAnExpression() {
        compose.setContent {
            AmountField(
                expression = "15+5",
                onExpressionChange = {},
                currencySymbol = "₴",
                previewResult = "20.00",
            )
        }

        compose.onNodeWithText("= 20.00").assertIsDisplayed()
    }

    @Test
    fun replacingTheTextKeepsTheFieldUsable() {
        var expression by mutableStateOf("249")
        compose.setContent {
            AmountField(
                expression = expression,
                onExpressionChange = { expression = it },
                currencySymbol = "₴",
                previewResult = null,
            )
        }

        compose.onNode(hasSetTextAction()).performTextReplacement("31.50")
        compose.waitForIdle()

        assertEquals("31.50", expression)
    }

    @Test
    fun theInvisibleSizerIsNotAnnouncedTwice() {
        // The field is measured by a transparent copy of its own text. That copy must not
        // reach the accessibility tree, or the amount is read out twice.
        compose.setContent {
            AmountField(
                expression = "249",
                onExpressionChange = {},
                currencySymbol = "₴",
                previewResult = null,
            )
        }

        // Would fail with "expected exactly one node" if the sizer still carried semantics.
        compose.onNodeWithText("249").assertIsDisplayed()
    }
}
