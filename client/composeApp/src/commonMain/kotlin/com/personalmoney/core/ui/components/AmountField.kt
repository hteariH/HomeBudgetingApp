package com.personalmoney.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The big centred amount, typed on the system keyboard.
 *
 * Replaces the in-app numpad this screen used to carry. The look is deliberately unchanged -
 * currency symbol, one oversized number, an optional `= result` line - because that display
 * is what the entry screen looks like; only the thing producing the digits moved to the
 * platform.
 *
 * [KeyboardType.Decimal] is what the system offers for money: digits, a decimal separator and
 * a minus. It does not offer `+ × ÷`, so arithmetic is no longer something a user can type
 * here - but [com.personalmoney.core.calc.Calculator] still parses it, so an expression that
 * arrives some other way (a paste, a receipt scan) still evaluates rather than being rejected.
 *
 * A comma needs no special handling: IMEs in ru/uk locales emit `,` as the decimal separator
 * and `Calculator` already folds it onto `.`, so the field shows what was actually typed.
 */
@Composable
fun AmountField(
    expression: String,
    onExpressionChange: (String) -> Unit,
    currencySymbol: String,
    previewResult: String?,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    onImeAction: () -> Unit = {},
) {
    // TextFieldValue rather than a plain String so the caret can be put at the end when the
    // text arrives from somewhere other than typing.
    var field by remember {
        mutableStateOf(TextFieldValue(expression, TextRange(expression.length)))
    }
    // Adopts a value set from outside - a scanned receipt, or the destination amount an FX
    // rate just recomputed - and equally undoes a character the view model rejected. During
    // ordinary typing the two agree, so this does nothing and the caret is left alone.
    LaunchedEffect(expression) {
        if (field.text != expression) {
            field = TextFieldValue(expression, TextRange(expression.length))
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        // Entry is the app's hot path: opening the screen should put the keyboard up, the
        // way tapping the numpad's first key used to be all it took.
        if (autoFocus) focusRequester.requestFocus()
    }

    val shown = field.text.ifEmpty { "0" }
    val amountStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = if (shown.length > 10) 40.sp else 56.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = currencySymbol,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(contentAlignment = Alignment.Center) {
                // An invisible twin of the text, to give the field a width. A BasicTextField
                // has no intrinsic width of its own: left alone it fills the row and drags
                // the number away from the currency symbol it is supposed to sit beside.
                //
                // Its semantics are cleared, not just its alpha: an invisible node is still
                // a node, and leaving it in the tree makes a screen reader announce the
                // amount twice.
                Text(
                    text = shown,
                    style = amountStyle,
                    modifier = Modifier
                        .alpha(0f)
                        .clearAndSetSemantics { },
                )

                if (field.text.isEmpty()) {
                    Text(
                        text = "0",
                        style = amountStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onExpressionChange(it.text)
                    },
                    textStyle = amountStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onImeAction() }),
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }
        }
        if (previewResult != null) {
            Text(
                text = "= $previewResult",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
