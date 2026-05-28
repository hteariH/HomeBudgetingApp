package com.personalmoney.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.ads.BannerAd
import com.personalmoney.core.database.entity.TransactionEntity
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.EmptyState
import com.personalmoney.core.ui.components.IconCircle
import com.personalmoney.core.ui.components.MoneyText
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transactions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.transactions_search))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showSearch) {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.transactions_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Type filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.filter.type == null,
                    onClick = { viewModel.setType(null) },
                    label = { Text(stringResource(R.string.currencies_all)) }
                )
                FilterChip(
                    selected = state.filter.type == TransactionType.EXPENSE,
                    onClick = {
                        viewModel.setType(if (state.filter.type == TransactionType.EXPENSE) null else TransactionType.EXPENSE)
                    },
                    label = { Text(stringResource(R.string.dashboard_add_expense)) }
                )
                FilterChip(
                    selected = state.filter.type == TransactionType.INCOME,
                    onClick = {
                        viewModel.setType(if (state.filter.type == TransactionType.INCOME) null else TransactionType.INCOME)
                    },
                    label = { Text(stringResource(R.string.dashboard_add_income)) }
                )
                FilterChip(
                    selected = state.filter.type == TransactionType.TRANSFER,
                    onClick = {
                        viewModel.setType(if (state.filter.type == TransactionType.TRANSFER) null else TransactionType.TRANSFER)
                    },
                    label = { Text(stringResource(R.string.dashboard_transfer)) }
                )
            }

            if (state.transactions.isEmpty()) {
                EmptyState(title = stringResource(R.string.transactions_empty))
            } else {
                TransactionList(state = state, onDelete = viewModel::delete)
            }
            BannerAd()
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@Composable
private fun TransactionList(state: TransactionsUiState, onDelete: (TransactionEntity) -> Unit) {
    // Group by date (LocalDate in current TZ)
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val yesterday = LocalDate(today.year, today.monthNumber, today.dayOfMonth).let {
        // subtract one day
        it.toEpochDays() - 1
    }.let { LocalDate.fromEpochDays(it) }

    val groups: List<Pair<LocalDate, List<TransactionEntity>>> = state.transactions
        .groupBy { it.occurredAt.toLocalDateTime(zone).date }
        .toSortedMap(compareByDescending { it })
        .toList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        groups.forEach { (date, items) ->
            val title = when (date) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> dateFormatter.format(date.toJavaLocalDate())
            }
            item(key = "header_${date}") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(items, key = { it.id }) { tx ->
                TransactionRow(
                    tx = tx,
                    accountName = state.accountsById[tx.accountId]?.name ?: "",
                    categoryName = tx.categoryId?.let { state.categoriesById[it]?.name },
                    categoryIcon = tx.categoryId?.let { state.categoriesById[it]?.icon } ?: "more_horiz",
                    categoryColor = tx.categoryId?.let { state.categoriesById[it]?.color } ?: 0xFF94A3B8L,
                    onLongPress = { onDelete(tx) }
                )
            }
        }
    }
}

private fun LocalDate.toJavaLocalDate(): java.time.LocalDate =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth)

@Composable
private fun TransactionRow(
    tx: TransactionEntity,
    accountName: String,
    categoryName: String?,
    categoryIcon: String,
    categoryColor: Long,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconCircle(iconKey = categoryIcon, color = Color(categoryColor), size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = categoryName ?: accountName, style = MaterialTheme.typography.titleSmall)
            if (!tx.note.isNullOrBlank()) {
                Text(
                    text = tx.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        MoneyText(
            money = Money(tx.accountAmountMinor, CurrencyCode(tx.currencyCode)),
            type = tx.type,
            showSign = true,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
