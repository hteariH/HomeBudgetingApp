package com.personalmoney.feature.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.AmountDisplay
import com.personalmoney.core.ui.components.CategoryChip
import com.personalmoney.core.ui.components.Numpad
import com.personalmoney.feature.quickadd.components.AccountPickerSheet
import com.personalmoney.feature.quickadd.components.DatePickerDialogWrapper
import com.personalmoney.feature.quickadd.components.NoCategoriesHint
import com.personalmoney.feature.quickadd.components.SelectedAccountBar
import kotlinx.coroutines.launch
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    initialType: String,
    initialCategoryId: Long?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAccountSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNoteInput by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar(context.getString(R.string.quickadd_saved))
            onSaved()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    val titleRes = if (state.type == TransactionType.INCOME) R.string.quickadd_title_income
    else R.string.quickadd_title_expense

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(R.string.quickadd_date))
                    }
                    IconButton(onClick = { showNoteInput = !showNoteInput }) {
                        Icon(Icons.Filled.EditNote, contentDescription = stringResource(R.string.quickadd_note))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Income / Expense toggle
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val options = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
                options.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(
                            stringResource(
                                if (type == TransactionType.INCOME) R.string.dashboard_add_income
                                else R.string.dashboard_add_expense
                            )
                        )
                    }
                }
            }

            // Account selector
            SelectedAccountBar(
                account = state.selectedAccount,
                onClick = { showAccountSheet = true }
            )

            // Amount display
            val currencySymbol = state.currencyCode?.let {
                runCatching { Currency.getInstance(it).getSymbol() }.getOrDefault(it)
            } ?: ""
            AmountDisplay(
                expression = state.expression,
                currencySymbol = currencySymbol,
                previewResult = state.previewResult,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Categories
            if (state.categories.isEmpty()) {
                NoCategoriesHint()
            } else {
                CategoriesRow(
                    categories = state.categories,
                    recentIds = state.recentCategoryIds,
                    selectedId = state.selectedCategory?.id,
                    onSelect = viewModel::selectCategory
                )
            }

            // Optional note
            if (showNoteInput) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    placeholder = { Text(stringResource(R.string.quickadd_note_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true
                )
            }

            HorizontalDivider(Modifier.padding(top = 4.dp))

            // Numpad
            Numpad(
                onKey = viewModel::onKey,
                doneEnabled = state.canSave,
                decimalSeparator = "."
            )
        }

        if (showAccountSheet) {
            AccountPickerSheet(
                accounts = state.accounts,
                selectedId = state.selectedAccount?.id,
                onPick = {
                    viewModel.selectAccount(it)
                    showAccountSheet = false
                },
                onDismiss = { showAccountSheet = false }
            )
        }

        if (showDatePicker) {
            DatePickerDialogWrapper(
                initial = state.date,
                onConfirm = {
                    viewModel.setDate(it)
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

@Composable
private fun CategoriesRow(
    categories: List<com.personalmoney.core.database.entity.CategoryEntity>,
    recentIds: List<Long>,
    selectedId: Long?,
    onSelect: (com.personalmoney.core.database.entity.CategoryEntity) -> Unit
) {
    // Ordered: selected first, then recents, then the rest
    val ordered = remember(categories, recentIds, selectedId) {
        val orderMap = recentIds.withIndex().associate { (i, id) -> id to i }
        categories.sortedWith(
            compareByDescending<com.personalmoney.core.database.entity.CategoryEntity> { it.id == selectedId }
                .thenBy { orderMap[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.sortOrder }
                .thenBy { it.name }
        )
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(ordered, key = { it.id }) { category ->
            CategoryChip(
                label = category.name,
                iconKey = category.icon,
                color = Color(category.color),
                selected = category.id == selectedId,
                onClick = { onSelect(category) }
            )
        }
    }
}
