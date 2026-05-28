package com.personalmoney.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Wallet
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finance = LocalFinanceColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.dashboard_settings)
                        )
                    }
                }
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
                        }
                    }
                }
            }

            // Quick actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        label = stringResource(R.string.dashboard_add_expense),
                        icon = Icons.Filled.Remove,
                        color = finance.expense,
                        onClick = onAddExpense,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionButton(
                        label = stringResource(R.string.dashboard_add_income),
                        icon = Icons.Filled.Add,
                        color = finance.income,
                        onClick = onAddIncome,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionButton(
                        label = stringResource(R.string.dashboard_transfer),
                        icon = Icons.Filled.CompareArrows,
                        color = finance.transfer,
                        onClick = onOpenTransfer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Navigation 2×2 grid — more breathing room for long labels
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NavTile(
                            label = stringResource(R.string.dashboard_accounts),
                            icon = Icons.Filled.Wallet,
                            onClick = onOpenAccounts,
                            modifier = Modifier.weight(1f)
                        )
                        NavTile(
                            label = stringResource(R.string.dashboard_categories),
                            icon = Icons.Filled.Category,
                            onClick = onOpenCategories,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NavTile(
                            label = stringResource(R.string.dashboard_subscriptions),
                            icon = Icons.Filled.Subscriptions,
                            onClick = onOpenSubscriptions,
                            modifier = Modifier.weight(1f)
                        )
                        NavTile(
                            label = stringResource(R.string.dashboard_transactions),
                            icon = Icons.AutoMirrored.Filled.List,
                            onClick = onOpenTransactions,
                            modifier = Modifier.weight(1f)
                        )
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
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 14.dp, horizontal = 6.dp)
                .fillMaxWidth()
                .heightIn(min = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NavTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
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
