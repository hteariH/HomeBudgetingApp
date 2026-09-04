package com.personalmoney.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ui.components.AmountField
import com.personalmoney.core.ui.components.CategoryChip
import com.personalmoney.core.ui.theme.PmButtonShape
import com.personalmoney.feature.quickadd.components.AccountPickerSheet
import com.personalmoney.feature.quickadd.components.DatePickerDialogWrapper
import com.personalmoney.feature.quickadd.components.SelectedAccountBar
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    onClose: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TransactionEditViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAccountSheet by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNoteInput by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.note) {
        if (state.note.isNotBlank()) showNoteInput = true
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar(getString(Res.string.quickadd_saved))
            onSaved()
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onSaved()
    }

    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.action_delete)) },
            text = { Text(stringResource(Res.string.transaction_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.transaction_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.action_close))
                    }
                },
                actions = {
                    if (!state.loading) {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(Res.string.quickadd_date))
                        }
                        IconButton(onClick = { showNoteInput = !showNoteInput }) {
                            Icon(Icons.Filled.EditNote, contentDescription = stringResource(Res.string.quickadd_note))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isTransfer) {
                Text(
                    text = stringResource(Res.string.transaction_transfer_readonly),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
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
                                    if (type == TransactionType.INCOME) Res.string.dashboard_add_income
                                    else Res.string.dashboard_add_expense
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

                val currencySymbol = state.currencyCode?.let {
                    (CurrencyRegistry.symbolFor(it) ?: it)
                } ?: ""
                // Not focused on open, unlike the quick-add screen: an edit usually starts
                // from wanting to change the category or the date, and throwing the keyboard
                // up would cover both.
                AmountField(
                    expression = state.expression,
                    onExpressionChange = viewModel::setAmountText,
                    currencySymbol = currencySymbol,
                    previewResult = state.previewResult,
                    onImeAction = { if (state.canSave) viewModel.save() },
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Categories
                if (state.categories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(state.categories, key = { it.id }) { category ->
                            CategoryChip(
                                label = category.name,
                                iconKey = category.icon,
                                color = androidx.compose.ui.graphics.Color(category.color),
                                selected = category.id == state.selectedCategory?.id,
                                onClick = { viewModel.selectCategory(category) }
                            )
                        }
                    }
                }
            }

            // Note input
            if (showNoteInput) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    placeholder = { Text(stringResource(Res.string.quickadd_note_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true
                )
            }

            // One save button for both cases now. A transfer row has always been
            // note-and-date only; with the numpad gone the editable case ends the same way.
            HorizontalDivider(Modifier.padding(top = 4.dp))
            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                shape = PmButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(Res.string.action_save))
            }
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
