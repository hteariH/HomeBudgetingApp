package com.personalmoney.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money

data class BreakdownSlice(
    val key: String,
    val label: String,
    val iconKey: String,
    val color: Color,
    val amount: Money
)

/**
 * Horizontal bar chart for category-style breakdowns.
 * Bars are scaled to the largest entry and animate when totals change.
 */
@Composable
fun CategoryBreakdownChart(
    slices: List<BreakdownSlice>,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) return
    val maxAmount = slices.maxOf { it.amount.minor }.coerceAtLeast(1L)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        slices.forEach { slice ->
            BreakdownRow(slice = slice, maxAmountMinor = maxAmount)
        }
    }
}

@Composable
private fun BreakdownRow(slice: BreakdownSlice, maxAmountMinor: Long) {
    val fraction = (slice.amount.minor.toFloat() / maxAmountMinor.toFloat()).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600),
        label = "bar"
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(iconKey = slice.iconKey, color = slice.color, size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = slice.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            MoneyText(money = slice.amount, style = MaterialTheme.typography.bodyMedium)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(50))
                    .background(slice.color)
            )
        }
    }
}

/**
 * Stacked horizontal proportions bar — total balance vs spending in one row.
 */
@Composable
fun StackedShareBar(
    slices: List<BreakdownSlice>,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.amount.minor }.coerceAtLeast(1L)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
    ) {
        slices.forEach { slice ->
            val weight = (slice.amount.minor.toFloat() / total.toFloat()).coerceAtLeast(0.001f)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(slice.color)
            )
        }
    }
}

@Suppress("unused")
internal fun previewSlices() = listOf(
    BreakdownSlice("a", "Food", "restaurant", Color(0xFFE5484D), Money(45000, CurrencyCode("USD"))),
    BreakdownSlice("b", "Transport", "directions_car", Color(0xFFFF9F1C), Money(12000, CurrencyCode("USD"))),
    BreakdownSlice("c", "Entertainment", "movie", Color(0xFFAB47BC), Money(8500, CurrencyCode("USD")))
)
