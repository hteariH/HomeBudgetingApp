package com.personalmoney.feature.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.core.ui.components.MoneyText
import com.personalmoney.core.ui.theme.ExpenseRed
import com.personalmoney.core.ui.theme.IncomeGreen
import com.personalmoney.core.ui.theme.LocalFinanceColors
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: AnalyticsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finance = LocalFinanceColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    title = stringResource(R.string.dashboard_add_expense),
                    money = state.totalExpenses,
                    color = finance.expense,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = stringResource(R.string.dashboard_add_income),
                    money = state.totalIncome,
                    color = finance.income,
                    modifier = Modifier.weight(1f)
                )
            }

            // Insights
            val expenseChange = state.expenseChangePercent
            val savingsRate = state.savingsRatePercent
            if (expenseChange != null || savingsRate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.analytics_insights),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (expenseChange != null) {
                            val text = when {
                                expenseChange > 0 -> stringResource(R.string.analytics_expense_up, expenseChange)
                                expenseChange < 0 -> stringResource(R.string.analytics_expense_down, -expenseChange)
                                else -> stringResource(R.string.analytics_expense_flat)
                            }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (expenseChange > 0) finance.expense else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (savingsRate != null) {
                            Text(
                                text = stringResource(R.string.analytics_savings_rate, savingsRate),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (savingsRate >= 0) finance.income else finance.expense
                            )
                        }
                    }
                }
            }

            val hasData = state.totalExpenses.minor > 0 || state.totalIncome.minor > 0

            if (!hasData) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = stringResource(R.string.chart_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            // Monthly bar chart
            if (hasData) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.analytics_by_month),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.analytics_last_months, state.months.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        MonthlyBarChart(
                            bars = state.months,
                            maxValue = state.maxValue
                        )
                        Spacer(Modifier.height(8.dp))
                        // Legend
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            LegendItem(
                                color = finance.expense,
                                label = stringResource(R.string.dashboard_add_expense)
                            )
                            LegendItem(
                                color = finance.income,
                                label = stringResource(R.string.dashboard_add_income)
                            )
                        }
                    }
                }
            }

            // Per-month detail list
            if (hasData) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.months.reversed().forEach { bar ->
                            MonthDetailRow(bar = bar, finance = finance)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    money: com.personalmoney.core.domain.Money,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            MoneyText(
                money = money,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
        }
    }
}

@Composable
private fun MonthlyBarChart(
    bars: List<MonthlyBar>,
    maxValue: Long,
    modifier: Modifier = Modifier
) {
    val chartHeight = 160.dp

    Row(
        modifier = modifier.fillMaxWidth().height(chartHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                val expFraction = (bar.expenses.minor.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                val incFraction = (bar.income.minor.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                val expAnim by animateFloatAsState(expFraction, tween(600), label = "exp")
                val incAnim by animateFloatAsState(incFraction, tween(600), label = "inc")

                // Two bars side-by-side per month
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight - 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Expense bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(expAnim)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(ExpenseRed)
                    )
                    // Income bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(incAnim)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(IncomeGreen)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MonthDetailRow(
    bar: MonthlyBar,
    finance: com.personalmoney.core.ui.theme.FinanceSemanticColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = bar.label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            if (bar.expenses.minor > 0) {
                MoneyText(
                    money = bar.expenses,
                    style = MaterialTheme.typography.bodySmall,
                    color = finance.expense
                )
            }
            if (bar.income.minor > 0) {
                MoneyText(
                    money = bar.income,
                    style = MaterialTheme.typography.bodySmall,
                    color = finance.income
                )
            }
            if (bar.expenses.minor == 0L && bar.income.minor == 0L) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Net
        val net = bar.income.minor - bar.expenses.minor
        if (net != 0L) {
            val netMoney = com.personalmoney.core.domain.Money(
                kotlin.math.abs(net),
                bar.expenses.currency
            )
            Text(
                text = if (net > 0) "+" else "−",
                style = MaterialTheme.typography.bodySmall,
                color = if (net > 0) finance.income else finance.expense
            )
            MoneyText(
                money = netMoney,
                style = MaterialTheme.typography.bodySmall,
                color = if (net > 0) finance.income else finance.expense
            )
        }
    }
}
