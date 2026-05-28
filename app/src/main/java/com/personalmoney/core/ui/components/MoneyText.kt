package com.personalmoney.core.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.MoneyFormatter
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.theme.LocalFinanceColors
import java.util.Locale

@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    type: TransactionType? = null,
    showSign: Boolean = false,
    locale: Locale = Locale.getDefault(),
    color: Color = LocalContentColor.current
) {
    val finance = LocalFinanceColors.current
    val resolvedColor = when (type) {
        TransactionType.INCOME -> finance.income
        TransactionType.EXPENSE -> finance.expense
        TransactionType.TRANSFER -> finance.transfer
        null -> color
    }
    val text = buildString {
        if (showSign && type == TransactionType.INCOME && !money.isZero()) append('+')
        if (showSign && type == TransactionType.EXPENSE && !money.isZero()) append('−')
        append(MoneyFormatter.format(money, locale))
    }
    Text(text = text, style = style, color = resolvedColor, modifier = modifier)
}
