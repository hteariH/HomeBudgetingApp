package com.personalmoney.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.personalmoney.core.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The searchable currency picker on a real device. Settings sits behind sign-in, so - as
 * with [AmountFieldTest] - the component is driven on its own: that it shows the current
 * selection, that typing narrows the menu, and that picking reports the code.
 */
class CurrencySelectTest {

    @get:Rule
    val compose = createComposeRule()

    private val currencies = listOf(
        Currency(code = "USD", name = "US Dollar", symbol = "$", active = true),
        Currency(code = "EUR", name = "Euro", symbol = "€", active = true),
        Currency(code = "UAH", name = "Ukrainian Hryvnia", symbol = "₴", active = true),
    )

    @Test
    fun showsTheSelectionWhileClosed() {
        compose.setContent {
            CurrencySelect(selected = "UAH", currencies = currencies, onSelect = {})
        }

        compose.onNodeWithText("UAH · Ukrainian Hryvnia").assertIsDisplayed()
    }

    @Test
    fun typingNarrowsTheMenu() {
        compose.setContent {
            CurrencySelect(selected = "UAH", currencies = currencies, onSelect = {})
        }

        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("eur")
        compose.waitForIdle()

        compose.onNodeWithText("EUR · Euro").assertIsDisplayed()
        compose.onAllNodesWithText("USD · US Dollar").assertCountEquals(0)
    }

    @Test
    fun pickingReportsTheCode() {
        var picked: String? = null
        compose.setContent {
            CurrencySelect(selected = "UAH", currencies = currencies, onSelect = { picked = it })
        }

        compose.onNode(hasSetTextAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("USD · US Dollar").performClick()
        compose.waitForIdle()

        assertEquals("USD", picked)
    }
}
