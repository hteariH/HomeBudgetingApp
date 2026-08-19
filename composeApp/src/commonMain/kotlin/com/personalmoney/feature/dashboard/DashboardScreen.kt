package com.personalmoney.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import com.personalmoney.core.ui.theme.ExpenseRed
import com.personalmoney.core.ui.theme.IncomeGreen
import com.personalmoney.feature.quickadd.QuickEntryPanel
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.draw.clip
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
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.ads.BannerAd
import com.personalmoney.core.domain.model.Transaction
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
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The wide dashboard is the React one: four headline figures, a six-month income and
    // spending chart, an account summary, and entry without leaving the page. None of that
    // fits on a phone, and none of it is web-specific - a tablet gets it too.
    BoxWithConstraints {
    val wide = maxWidth >= WIDE_DASHBOARD

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.dashboard_title)) }
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
            if (wide) {
                item {
                    StatCardRow(state)
                }
                item {
                    QuickEntryPanel()
                }
            }

            // Total balance card. Skipped on a wide window, where the stat row above already
            // carries this figure and the two beneath it - three cards saying the same thing
            // is not a richer dashboard.
            if (!wide) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(Res.string.dashboard_total_balance),
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

            // Month overview. Also folded into the stat row on a wide window.
            if (!wide) item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = stringResource(Res.string.dashboard_add_expense),
                        money = state.monthExpenses,
                        type = TransactionType.EXPENSE,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(Res.string.dashboard_add_income),
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
                                text = stringResource(Res.string.chart_expenses_by_category),
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
                                    text = stringResource(Res.string.dashboard_more_categories, state.moreExpenseCategories),
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
                                text = stringResource(Res.string.dashboard_budgets),
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
                    text = stringResource(Res.string.dashboard_recent),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (state.recent.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.dashboard_no_transactions),
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

            if (wide) {
                item { MonthlyFlowCard(state) }
                item { AccountsSummaryCard(state) }
            }

            item { BannerAd() }
        }
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
    transaction: Transaction,
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

/**
 * Where the dashboard stops being a phone screen.
 *
 * Higher than the navigation rail's 840dp: the rail only needs room for itself, while these
 * cards need four figures side by side and a chart wide enough to read six months on.
 */
private val WIDE_DASHBOARD = 900.dp

/** The four headline figures, side by side. Mirrors the React dashboard's stat grid. */
@Composable
private fun StatCardRow(state: DashboardUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            label = stringResource(Res.string.dashboard_net_worth),
            money = state.totalBalance,
            sub = pluralStringResource(
                Res.plurals.dashboard_account_count,
                state.totalAccountsCount,
                state.totalAccountsCount,
            ),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(Res.string.dashboard_month_income),
            money = state.monthIncome,
            valueColor = IncomeGreen,
            sub = stringResource(Res.string.dashboard_current_month),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(Res.string.dashboard_month_expenses),
            money = state.monthExpenses,
            valueColor = ExpenseRed,
            sub = stringResource(Res.string.dashboard_current_month),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(Res.string.dashboard_net_this_month),
            money = state.monthNet,
            valueColor = if (state.monthNet.minor >= 0) IncomeGreen else ExpenseRed,
            sub = stringResource(Res.string.dashboard_income_minus_expenses),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    money: Money,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(6.dp))
            MoneyText(
                money = money,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Income against spending, six months.
 *
 * Grouped bars rather than the React version's overlapping areas: two filled areas that
 * cross each other are hard to read at this size, and paired bars make the month-by-month
 * comparison - which is the actual question - a direct one.
 *
 * Income is always the left bar and spending always the right, and both are named in the
 * legend. That is deliberate rather than decorative: the app's income-green and expense-red
 * are only 4.9 apart in OKLab under deuteranopia, so position and the legend carry the
 * identity and colour merely reinforces it.
 */
@Composable
private fun MonthlyFlowCard(state: DashboardUiState) {
    val flow = state.monthlyFlow
    if (flow.isEmpty()) return
    val peak = flow.maxOf { maxOf(it.incomeMinor, it.expenseMinor) }.coerceAtLeast(1L)

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.dashboard_chart_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(16.dp))
            Row(
                Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                flow.forEach { month ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            FlowBar(month.incomeMinor, peak, IncomeGreen)
                            Spacer(Modifier.size(4.dp))
                            FlowBar(month.expenseMinor, peak, ExpenseRed)
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(
                            month.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendSwatch(IncomeGreen, stringResource(Res.string.common_income))
                LegendSwatch(ExpenseRed, stringResource(Res.string.common_expense))
            }
        }
    }
}

@Composable
private fun RowScope.FlowBar(valueMinor: Long, peakMinor: Long, color: Color) {
    // A zero month still draws a hairline, so "spent nothing" reads as a value rather than
    // as a column the chart forgot to fill.
    val fraction = (valueMinor.toFloat() / peakMinor).coerceIn(0.01f, 1f)
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight(fraction)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(color)
    )
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Every account and its own balance - the one thing the phone dashboard never showed. */
@Composable
private fun AccountsSummaryCard(state: DashboardUiState) {
    if (state.accounts.isEmpty()) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.dashboard_accounts),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(8.dp))
            state.accounts.forEach { account ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(account.color))
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(account.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    MoneyText(
                        money = Money(account.balanceMinor, CurrencyCode(account.currencyCode)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
