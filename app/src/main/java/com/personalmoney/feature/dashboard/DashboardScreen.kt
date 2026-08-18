package com.personalmoney.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.ads.BannerAd
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.BreakdownSlice
import com.personalmoney.core.ui.components.CategoryBreakdownChart
import com.personalmoney.core.ui.components.IconCircle
import com.personalmoney.core.ui.components.MoneyText
import com.personalmoney.core.ui.theme.LocalFinanceColors
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Total balance card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.dashboard_total_balance),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.size(4.dp))
                        MoneyText(
                            money = state.totalBalance,
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val trend = state.netWorthTrend
                        if (trend.size >= 2 && trend.min() != trend.max()) {
                            Spacer(Modifier.size(12.dp))
                            Sparkline(
                                values = trend,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            )
                        }
                    }
                }
            }

            // Month overview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = stringResource(R.string.dashboard_add_expense),
                        money = state.monthExpenses,
                        type = TransactionType.EXPENSE,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.dashboard_add_income),
                        money = state.monthIncome,
                        type = TransactionType.INCOME,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Expense breakdown chart
            if (state.expenseByCategory.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.chart_expenses_by_category),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.size(12.dp))
                            val slices = remember(state.expenseByCategory) {
                                state.expenseByCategory.map { s ->
                                    BreakdownSlice(
                                        key = "cat-${s.categoryId}",
                                        label = s.name,
                                        iconKey = s.icon,
                                        color = Color(s.color),
                                        amount = Money(
                                            s.totalMinor,
                                            CurrencyCode(state.baseCurrency)
                                        )
                                    )
                                }
                            }
                            CategoryBreakdownChart(slices = slices)
                            if (state.moreExpenseCategories > 0) {
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_more_categories, state.moreExpenseCategories),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Budgets
            if (state.budgets.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.dashboard_budgets),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.size(12.dp))
                            state.budgets.forEachIndexed { i, budget ->
                                if (i > 0) Spacer(Modifier.size(12.dp))
                                BudgetRow(budget = budget, baseCurrency = state.baseCurrency)
                            }
                        }
                    }
                }
            }

            // Recent transactions
            item {
                Text(
                    text = stringResource(R.string.dashboard_recent),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (state.recent.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_no_transactions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(state.recent, key = { it.id }) { tx ->
                    RecentTransactionRow(
                        transaction = tx,
                        accountName = state.accountsById[tx.accountId]?.name ?: "",
                        categoryName = tx.categoryId?.let { state.categoriesById[it]?.name },
                        categoryIcon = tx.categoryId?.let { state.categoriesById[it]?.icon }
                            ?: "more_horiz",
                        categoryColor = tx.categoryId?.let { state.categoriesById[it]?.color }
                            ?: 0xFF94A3B8L
                    )
                }
            }

            item { BannerAd() }
        }
    }
}

@Composable
private fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val min = values.min()
    val range = (values.max() - min).coerceAtLeast(1L)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stepX = if (values.size > 1) w / (values.size - 1) else w
        val points = values.mapIndexed { i, v ->
            val x = stepX * i
            val y = h - ((v - min).toFloat() / range.toFloat()) * h
            Offset(x, y)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun BudgetRow(budget: BudgetProgress, baseCurrency: String) {
    val finance = LocalFinanceColors.current
    val currency = CurrencyCode(baseCurrency)
    val barColor = if (budget.isOver) finance.expense else MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconCircle(iconKey = budget.icon, color = Color(budget.color), size = 32.dp)
            Text(
                text = budget.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            MoneyText(
                money = Money(budget.spentMinor, currency),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (budget.isOver) finance.expense else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = " / ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MoneyText(
                money = Money(budget.limitMinor, currency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(6.dp))
        LinearProgressIndicator(
            progress = { budget.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    money: Money,
    type: TransactionType,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(4.dp))
            MoneyText(
                money = money,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                type = type
            )
        }
    }
}

@Composable
private fun RecentTransactionRow(
    transaction: TransactionEntity,
    accountName: String,
    categoryName: String?,
    categoryIcon: String,
    categoryColor: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconCircle(iconKey = categoryIcon, color = Color(categoryColor), size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName ?: transaction.note ?: accountName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val ldt: LocalDateTime =
                transaction.occurredAt.toLocalDateTime(TimeZone.currentSystemDefault())
            Text(
                text = "${ldt.dayOfMonth}.${ldt.monthNumber.toString().padStart(2, '0')} · $accountName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MoneyText(
            money = Money(transaction.accountAmountMinor, CurrencyCode(transaction.currencyCode)),
            type = transaction.type,
            showSign = true,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
