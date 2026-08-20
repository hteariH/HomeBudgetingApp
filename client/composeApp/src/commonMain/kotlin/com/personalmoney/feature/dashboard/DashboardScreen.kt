package com.personalmoney.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import com.personalmoney.core.ui.theme.LocalFinanceColors
import com.personalmoney.feature.quickadd.QuickEntryPanel
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.personalmoney.core.ui.PageWidth
import com.personalmoney.core.ui.components.MoneyText
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenAccount: () -> Unit = {},
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
        if (wide) {
            WideDashboard(state, { id -> viewModel.openAccount(id); onOpenAccount() }, Modifier.padding(padding))
            return@Scaffold
        }
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

            // Month overview
            item {
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
 * cards need four figures side by side and a chart wide enough to read six months on. It is
 * the same width at which an entry form fits inline, which is why it is one shared number.
 */
private val WIDE_DASHBOARD = PageWidth.InlineEntry

/**
 * The month's three figures.
 *
 * Net worth is not among them on purpose: the balance column beside this already leads with
 * it, and repeating it would be the third card on screen showing one number.
 */
@Composable
private fun StatCardRow(state: DashboardUiState) {
    val finance = LocalFinanceColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            label = stringResource(Res.string.dashboard_month_income),
            money = state.monthIncome,
            valueColor = finance.income,
            sub = stringResource(Res.string.dashboard_current_month),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(Res.string.dashboard_month_expenses),
            money = state.monthExpenses,
            valueColor = finance.expense,
            sub = stringResource(Res.string.dashboard_current_month),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = stringResource(Res.string.dashboard_net_this_month),
            money = state.monthNet,
            valueColor = if (state.monthNet.minor >= 0) finance.income else finance.expense,
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
    val finance = LocalFinanceColors.current
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
                            FlowBar(month.incomeMinor, peak, finance.income)
                            Spacer(Modifier.size(4.dp))
                            FlowBar(month.expenseMinor, peak, finance.expense)
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
                LegendSwatch(finance.income, stringResource(Res.string.common_income))
                LegendSwatch(finance.expense, stringResource(Res.string.common_expense))
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
private fun AccountsSummaryCard(state: DashboardUiState, onOpenAccount: (Long) -> Unit) {
    if (state.accounts.isEmpty()) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(Res.string.dashboard_accounts),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.size(8.dp))
            state.accounts.forEach { account ->
                Row(
                    // A balance invites the question "made of what?", and the answer is the
                    // ledger filtered to this account. The whole row is the target rather
                    // than the name alone: a two-word account name is a small thing to hit,
                    // and the amount is the part being asked about.
                    //
                    // The padding moved off the Column and onto the row so the ripple runs
                    // to the card's edge instead of stopping short of it.
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccount(account.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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

/**
 * The wide dashboard: two columns, not one long scroll.
 *
 * Modelled on the layout the owner asked for - balances down the left, and everything you
 * *do* down the right: enter a transaction, see what just happened, see where the month
 * went. A single centred column is a phone screen on a big monitor; this uses the width for
 * the thing width is good at, which is showing two related things at once.
 *
 * Each column scrolls on its own, so a long account list does not push the entry panel off
 * the top.
 */
@Composable
private fun WideDashboard(
    state: DashboardUiState,
    onOpenAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Narrower, and fixed: an account list is names and numbers, and giving it half the
        // window would only stretch the gap between the two.
        Column(
            Modifier.weight(0.36f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BalanceHeader(state)
            AccountsSummaryCard(state, onOpenAccount)
        }

        Column(
            Modifier.weight(0.64f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCardRow(state)
            QuickEntryPanel()
            RecentCard(state)
            ExpenseBreakdownCard(state)
            MonthlyFlowCard(state)
            BannerAd()
        }
    }
}

/** The running total, at the head of the balance column. */
@Composable
private fun BalanceHeader(state: DashboardUiState) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(Res.string.dashboard_total_balance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(4.dp))
            MoneyText(
                money = state.totalBalance,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                pluralStringResource(
                    Res.plurals.dashboard_account_count,
                    state.totalAccountsCount,
                    state.totalAccountsCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/** Recent rows as a card, so the right column reads as a stack of panels. */
@Composable
private fun RecentCard(state: DashboardUiState) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.dashboard_recent),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(8.dp))
            if (state.recent.isEmpty()) {
                Text(
                    stringResource(Res.string.dashboard_no_transactions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.recent.forEach { tx ->
                    RecentTransactionRow(
                        transaction = tx,
                        accountName = state.accountsById[tx.accountId]?.name ?: "",
                        categoryName = tx.categoryId?.let { state.categoriesById[it]?.name },
                        categoryIcon = tx.categoryId?.let { state.categoriesById[it]?.icon }
                            ?: "more_horiz",
                        categoryColor = tx.categoryId?.let { state.categoriesById[it]?.color }
                            ?: 0xFF94A3B8L,
                    )
                }
            }
        }
    }
}

/** The same breakdown the phone shows, lifted out so both layouts call one thing. */
@Composable
private fun ExpenseBreakdownCard(state: DashboardUiState) {
    if (state.expenseByCategory.isEmpty()) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.chart_expenses_by_category),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(12.dp))
            val slices = remember(state.expenseByCategory) {
                state.expenseByCategory.map { s ->
                    BreakdownSlice(
                        key = "cat-${s.categoryId}",
                        label = s.name,
                        iconKey = s.icon,
                        color = Color(s.color),
                        amount = Money(s.totalMinor, CurrencyCode(state.baseCurrency)),
                    )
                }
            }
            CategoryBreakdownChart(slices = slices)
            if (state.moreExpenseCategories > 0) {
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        Res.string.dashboard_more_categories,
                        state.moreExpenseCategories,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
