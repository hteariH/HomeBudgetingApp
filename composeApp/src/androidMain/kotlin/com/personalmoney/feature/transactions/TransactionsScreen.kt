package com.personalmoney.feature.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.ads.BannerAd
import com.personalmoney.core.domain.model.Transaction
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.EmptyState
import com.personalmoney.core.ui.components.IconCircle
import com.personalmoney.core.ui.components.MoneyText
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

private enum class PeriodFilter { ALL, TODAY, WEEK, MONTH, YEAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onBack: (() -> Unit)? = null,
    onEdit: (Long) -> Unit = {},
    viewModel: TransactionsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var activePeriod by remember { mutableStateOf(PeriodFilter.ALL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.transactions_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                        }
                    }
                },
                actions = {
                    val anyFilter = activePeriod != PeriodFilter.ALL ||
                        state.filter.type != null ||
                        state.filter.query.isNotBlank() ||
                        state.filter.accountId != null ||
                        state.filter.categoryId != null
                    if (anyFilter) {
                        IconButton(onClick = {
                            viewModel.clearFilters()
                            activePeriod = PeriodFilter.ALL
                            showSearch = false
                        }) {
                            Icon(
                                Icons.Filled.FilterAltOff,
                                contentDescription = stringResource(Res.string.action_clear_filters)
                            )
                        }
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.transactions_search))
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
                    placeholder = { Text(stringResource(Res.string.transactions_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Filters as compact dropdowns in a single row
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(zone).date
            val accountFilters = state.accountsById.values
                .filter { !it.archived }
                .sortedWith(compareBy({ it.sortOrder }, { it.name }))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val periodLabel = when (activePeriod) {
                    PeriodFilter.ALL -> stringResource(Res.string.filter_period)
                    PeriodFilter.TODAY -> stringResource(Res.string.period_today)
                    PeriodFilter.WEEK -> stringResource(Res.string.period_week)
                    PeriodFilter.MONTH -> stringResource(Res.string.period_month)
                    PeriodFilter.YEAR -> stringResource(Res.string.period_year)
                }
                FilterDropdown(
                    label = periodLabel,
                    active = activePeriod != PeriodFilter.ALL,
                    modifier = Modifier.weight(1f)
                ) { dismiss ->
                    val pick: (PeriodFilter, LocalDate?, LocalDate?) -> Unit = { p, from, to ->
                        activePeriod = p
                        viewModel.setDateRange(from, to)
                        dismiss()
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.period_all)) },
                        onClick = { pick(PeriodFilter.ALL, null, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.period_today)) },
                        onClick = { pick(PeriodFilter.TODAY, today, today) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.period_week)) },
                        onClick = {
                            val weekStart = LocalDate.fromEpochDays(today.toEpochDays() - today.dayOfWeek.ordinal)
                            pick(PeriodFilter.WEEK, weekStart, today)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.period_month)) },
                        onClick = { pick(PeriodFilter.MONTH, LocalDate(today.year, today.month, 1), today) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.period_year)) },
                        onClick = { pick(PeriodFilter.YEAR, LocalDate(today.year, 1, 1), today) }
                    )
                }

                val typeLabel = when (state.filter.type) {
                    null -> stringResource(Res.string.filter_type)
                    TransactionType.EXPENSE -> stringResource(Res.string.dashboard_add_expense)
                    TransactionType.INCOME -> stringResource(Res.string.dashboard_add_income)
                    TransactionType.TRANSFER -> stringResource(Res.string.dashboard_transfer)
                }
                FilterDropdown(
                    label = typeLabel,
                    active = state.filter.type != null,
                    modifier = Modifier.weight(1f)
                ) { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.transactions_all)) },
                        onClick = { viewModel.setType(null); dismiss() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.dashboard_add_expense)) },
                        onClick = { viewModel.setType(TransactionType.EXPENSE); dismiss() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.dashboard_add_income)) },
                        onClick = { viewModel.setType(TransactionType.INCOME); dismiss() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.dashboard_transfer)) },
                        onClick = { viewModel.setType(TransactionType.TRANSFER); dismiss() }
                    )
                }

                if (accountFilters.size > 1) {
                    val accountLabel = accountFilters.firstOrNull { it.id == state.filter.accountId }?.name
                        ?: stringResource(Res.string.filter_account)
                    FilterDropdown(
                        label = accountLabel,
                        active = state.filter.accountId != null,
                        modifier = Modifier.weight(1f)
                    ) { dismiss ->
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.transactions_all)) },
                            onClick = { viewModel.setAccount(null); dismiss() }
                        )
                        accountFilters.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(account.color))
                                    )
                                },
                                onClick = { viewModel.setAccount(account.id); dismiss() }
                            )
                        }
                    }
                }
            }

            if (state.transactions.isEmpty()) {
                EmptyState(title = stringResource(Res.string.transactions_empty))
            } else {
                TransactionList(
                    state = state,
                    onEdit = onEdit,
                    onDelete = viewModel::delete,
                    onDuplicate = viewModel::duplicate
                )
            }
            BannerAd()
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionList(
    state: TransactionsUiState,
    onEdit: (Long) -> Unit,
    onDelete: (Transaction) -> Unit,
    onDuplicate: (Transaction) -> Unit
) {
    var deleteTarget by remember { mutableStateOf<Transaction?>(null) }

    deleteTarget?.let { tx ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(Res.string.action_delete)) },
            text = { Text(stringResource(Res.string.transaction_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tx)
                    deleteTarget = null
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    val todayLabel = stringResource(Res.string.transactions_today)
    val yesterdayLabel = stringResource(Res.string.transactions_yesterday)

    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val yesterday = LocalDate.fromEpochDays(today.toEpochDays() - 1)

    val groups: List<Pair<LocalDate, List<Transaction>>> = state.transactions
        .groupBy { it.occurredAt.toLocalDateTime(zone).date }
        .toSortedMap(compareByDescending { it })
        .toList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        groups.forEach { (date, items) ->
            val title = when (date) {
                today -> todayLabel
                yesterday -> yesterdayLabel
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
                val isTransfer = tx.type == TransactionType.TRANSFER
                TransactionRow(
                    tx = tx,
                    accountName = state.accountsById[tx.accountId]?.name ?: "",
                    counterpartName = tx.transferCounterpartAccountId?.let { state.accountsById[it]?.name },
                    counterpartTx = state.transferCounterpartsById[tx.id],
                    categoryName = tx.categoryId?.let { state.categoriesById[it]?.name },
                    categoryIcon = if (isTransfer) "currency_exchange"
                        else tx.categoryId?.let { state.categoriesById[it]?.icon } ?: "more_horiz",
                    categoryColor = tx.categoryId?.let { state.categoriesById[it]?.color } ?: 0xFF94A3B8L,
                    onClick = { onEdit(tx.id) },
                    onDuplicate = { onDuplicate(tx) },
                    onDelete = { deleteTarget = tx }
                )
            }
        }
    }
}

private fun LocalDate.toJavaLocalDate(): java.time.LocalDate =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    tx: Transaction,
    accountName: String,
    counterpartName: String?,
    counterpartTx: Transaction?,
    categoryName: String?,
    categoryIcon: String,
    categoryColor: Long,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isTransfer = tx.type == TransactionType.TRANSFER
    // For a transfer the stored leg is the source when its amount is negative; the counterpart is the
    // other side. Render directionally as "source -> destination" regardless of which leg survived.
    val title = if (isTransfer) {
        val other = counterpartName.orEmpty()
        if (tx.accountAmountMinor < 0) "$accountName → $other" else "$other → $accountName"
    } else {
        categoryName ?: accountName
    }
    val subtitle = when {
        !tx.note.isNullOrBlank() -> tx.note
        isTransfer -> stringResource(Res.string.dashboard_transfer)
        else -> accountName
    }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconCircle(iconKey = categoryIcon, color = Color(categoryColor), size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Cross-currency transfer: show both legs (source over destination). Otherwise a single amount.
            if (isTransfer && counterpartTx != null && counterpartTx.currencyCode != tx.currencyCode) {
                val sourceLeg = if (tx.accountAmountMinor < 0) tx else counterpartTx
                val destLeg = if (tx.accountAmountMinor < 0) counterpartTx else tx
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(
                        money = Money(kotlin.math.abs(sourceLeg.accountAmountMinor), CurrencyCode(sourceLeg.currencyCode)),
                        type = TransactionType.TRANSFER,
                        style = MaterialTheme.typography.titleMedium
                    )
                    MoneyText(
                        money = Money(kotlin.math.abs(destLeg.accountAmountMinor), CurrencyCode(destLeg.currencyCode)),
                        type = TransactionType.TRANSFER,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                MoneyText(
                    money = Money(
                        if (isTransfer) kotlin.math.abs(tx.accountAmountMinor) else tx.accountAmountMinor,
                        CurrencyCode(tx.currencyCode)
                    ),
                    type = tx.type,
                    showSign = true,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (tx.transferGroupId == null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_duplicate)) },
                    onClick = { menuOpen = false; onDuplicate() }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.action_delete)) },
                onClick = { menuOpen = false; onDelete() }
            )
        }
    }
}

/**
 * A compact filter selector: a bordered (or filled, when [active]) pill showing the current value
 * and a dropdown arrow; tapping opens a [DropdownMenu] built by [menuContent]. The lambda receives a
 * `dismiss` callback to close the menu after a selection.
 */
@Composable
private fun FilterDropdown(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
        }
    }
}
