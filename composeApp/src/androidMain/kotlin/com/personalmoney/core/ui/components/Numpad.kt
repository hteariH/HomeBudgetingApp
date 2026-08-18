package com.personalmoney.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.personalmoney.core.calc.NumpadKey


@Composable
fun Numpad(
    onKey: (NumpadKey) -> Unit,
    doneEnabled: Boolean = true,
    decimalSeparator: String = ".",
    showOperators: Boolean = true,
    phoneLayout: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rows: List<List<NumpadKey>> = if (showOperators) {
        if (phoneLayout) listOf(
            listOf(NumpadKey.DIGIT_1, NumpadKey.DIGIT_2, NumpadKey.DIGIT_3, NumpadKey.DIVIDE),
            listOf(NumpadKey.DIGIT_4, NumpadKey.DIGIT_5, NumpadKey.DIGIT_6, NumpadKey.MULTIPLY),
            listOf(NumpadKey.DIGIT_7, NumpadKey.DIGIT_8, NumpadKey.DIGIT_9, NumpadKey.MINUS),
            listOf(NumpadKey.DOT, NumpadKey.DIGIT_0, NumpadKey.BACKSPACE, NumpadKey.PLUS),
            listOf(NumpadKey.DONE)
        ) else listOf(
            listOf(NumpadKey.DIGIT_7, NumpadKey.DIGIT_8, NumpadKey.DIGIT_9, NumpadKey.DIVIDE),
            listOf(NumpadKey.DIGIT_4, NumpadKey.DIGIT_5, NumpadKey.DIGIT_6, NumpadKey.MULTIPLY),
            listOf(NumpadKey.DIGIT_1, NumpadKey.DIGIT_2, NumpadKey.DIGIT_3, NumpadKey.MINUS),
            listOf(NumpadKey.DOT, NumpadKey.DIGIT_0, NumpadKey.BACKSPACE, NumpadKey.PLUS),
            listOf(NumpadKey.DONE)
        )
    } else {
        if (phoneLayout) listOf(
            listOf(NumpadKey.DIGIT_1, NumpadKey.DIGIT_2, NumpadKey.DIGIT_3),
            listOf(NumpadKey.DIGIT_4, NumpadKey.DIGIT_5, NumpadKey.DIGIT_6),
            listOf(NumpadKey.DIGIT_7, NumpadKey.DIGIT_8, NumpadKey.DIGIT_9),
            listOf(NumpadKey.DOT, NumpadKey.DIGIT_0, NumpadKey.BACKSPACE),
            listOf(NumpadKey.DONE)
        ) else listOf(
            listOf(NumpadKey.DIGIT_7, NumpadKey.DIGIT_8, NumpadKey.DIGIT_9),
            listOf(NumpadKey.DIGIT_4, NumpadKey.DIGIT_5, NumpadKey.DIGIT_6),
            listOf(NumpadKey.DIGIT_1, NumpadKey.DIGIT_2, NumpadKey.DIGIT_3),
            listOf(NumpadKey.DOT, NumpadKey.DIGIT_0, NumpadKey.BACKSPACE),
            listOf(NumpadKey.DONE)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowKeys.forEach { key ->
                    val isDone = key == NumpadKey.DONE
                    val weight = if (isDone) rowKeys.size.toFloat() else 1f
                    KeyButton(
                        key = key,
                        decimalSeparator = decimalSeparator,
                        modifier = Modifier
                            .weight(weight)
                            .heightIn(min = 56.dp),
                        enabled = !isDone || doneEnabled,
                        onClick = onKey
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    key: NumpadKey,
    decimalSeparator: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (NumpadKey) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isOperator = key in setOf(NumpadKey.PLUS, NumpadKey.MINUS, NumpadKey.MULTIPLY, NumpadKey.DIVIDE)
    val isDone = key == NumpadKey.DONE
    // Spoken label for screen readers — raw symbols like ⌫/✓/× don't read well.
    val keyDescription = when (key) {
        NumpadKey.PLUS -> stringResource(Res.string.a11y_plus)
        NumpadKey.MINUS -> stringResource(Res.string.a11y_minus)
        NumpadKey.MULTIPLY -> stringResource(Res.string.a11y_multiply)
        NumpadKey.DIVIDE -> stringResource(Res.string.a11y_divide)
        NumpadKey.DOT -> stringResource(Res.string.a11y_decimal)
        NumpadKey.BACKSPACE -> stringResource(Res.string.a11y_backspace)
        NumpadKey.DONE -> stringResource(Res.string.a11y_done)
        else -> key.display
    }
    val container: Color = when {
        isDone -> if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        isOperator -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer: Color = when {
        isDone -> if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick(key)
            }
            .semantics { contentDescription = keyDescription },
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            when (key) {
                NumpadKey.BACKSPACE -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = onContainer
                )
                NumpadKey.DONE -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = onContainer
                )
                NumpadKey.DOT -> Text(
                    text = decimalSeparator,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
                    color = onContainer,
                    textAlign = TextAlign.Center
                )
                else -> Text(
                    text = key.display,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
                    color = onContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
