package com.personalmoney.feature.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.personalmoney.core.ui.verticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.currency.CurrencyRegistry
import com.personalmoney.core.calc.Calculator
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.ocr.ScanFailure
import com.personalmoney.core.ocr.receiptScanAvailable
import com.personalmoney.core.ocr.rememberReceiptScanner
import com.personalmoney.core.ui.components.AmountField
import com.personalmoney.core.ui.components.CategoryChip
import com.personalmoney.core.ui.theme.PmButtonShape
import com.personalmoney.feature.quickadd.components.AccountPickerSheet
import com.personalmoney.feature.quickadd.components.DatePickerDialogWrapper
import com.personalmoney.feature.quickadd.components.NoCategoriesHint
import com.personalmoney.feature.quickadd.components.SelectedAccountBar
import com.personalmoney.feature.transfers.TransferViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    initialType: String,
    initialCategoryId: Long?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onPickAccount: (Long?) -> Unit,
    viewModel: QuickAddViewModel = koinViewModel(),
    transferViewModel: TransferViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transferState by transferViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var mode by remember {
        mutableStateOf(
            when (initialType) {
                "TRANSFER" -> EntryMode.TRANSFER
                "INCOME" -> EntryMode.INCOME
                else -> EntryMode.EXPENSE
            }
        )
    }
    var showFee by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNoteInput by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var scanFailure by remember { mutableStateOf<ScanFailure?>(null) }

    // A scan fills the form and stops - it never saves. Everything it found stays editable,
    // which is the only safe contract for a number read off a crumpled thermal print.
    val scanReceipt = rememberReceiptScanner(
        onScanned = { receipt ->
            receipt.total?.let(viewModel::setAmountText)
            receipt.date?.let(viewModel::setDate)
            receipt.merchant?.let { merchant ->
                viewModel.setNote(merchant)
                // Opened rather than left folded away: a note the user cannot see is a note
                // they cannot correct, and the shop name is the field OCR gets wrong most.
                showNoteInput = true
            }
        },
        onFailed = { scanFailure = it },
    )

    // Opened in transfer mode: keep the quick-add VM's type strictly EXPENSE so its category
    // flow / canSave stay coherent (transfers are driven entirely by transferViewModel).
    LaunchedEffect(Unit) {
        if (initialType == "TRANSFER") viewModel.setType(TransactionType.EXPENSE)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            snackbarHostState.showSnackbar(getString(Res.string.quickadd_saved))
            onSaved()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }
    LaunchedEffect(transferState.saved) {
        if (mode == EntryMode.TRANSFER && transferState.saved) {
            snackbarHostState.showSnackbar(getString(Res.string.quickadd_saved))
            onSaved()
        }
    }
    LaunchedEffect(scanFailure) {
        val failure = scanFailure ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            getString(
                when (failure) {
                    ScanFailure.NOTHING_FOUND -> Res.string.quickadd_scan_nothing_found
                    ScanFailure.ENGINE_ERROR -> Res.string.quickadd_scan_failed
                }
            )
        )
        scanFailure = null
    }
    LaunchedEffect(transferState.error) {
        transferState.error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            transferViewModel.clearError()
        }
    }

    val titleRes = when (mode) {
        EntryMode.TRANSFER -> Res.string.transfer_title
        EntryMode.INCOME -> Res.string.quickadd_title_income
        EntryMode.EXPENSE -> Res.string.quickadd_title_expense
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.action_close))
                    }
                },
                actions = {
                    // No scanning in transfer mode: a receipt records a purchase, and a
                    // transfer moves money between the user's own accounts.
                    if (receiptScanAvailable && mode != EntryMode.TRANSFER) {
                        IconButton(onClick = scanReceipt) {
                            Icon(
                                Icons.Filled.DocumentScanner,
                                contentDescription = stringResource(Res.string.quickadd_scan_receipt)
                            )
                        }
                    }
                    if (mode != EntryMode.TRANSFER) {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(Res.string.quickadd_date))
                        }
                    }
                    IconButton(onClick = { showNoteInput = !showNoteInput }) {
                        Icon(Icons.Filled.EditNote, contentDescription = stringResource(Res.string.quickadd_note))
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
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Expense / Income / Transfer toggle — pinned at the top
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val options = listOf(EntryMode.EXPENSE, EntryMode.INCOME, EntryMode.TRANSFER)
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = {
                            when (option) {
                                EntryMode.INCOME -> viewModel.setType(TransactionType.INCOME)
                                EntryMode.EXPENSE -> viewModel.setType(TransactionType.EXPENSE)
                                EntryMode.TRANSFER -> {}
                            }
                            mode = option
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(
                            stringResource(
                                when (option) {
                                    EntryMode.EXPENSE -> Res.string.dashboard_add_expense
                                    EntryMode.INCOME -> Res.string.dashboard_add_income
                                    EntryMode.TRANSFER -> Res.string.transfer_mode
                                }
                            )
                        )
                    }
                }
            }

            // Scrollable middle region so the save button always stays pinned on-screen —
            // even when transfer mode adds the destination amount and the fee.
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            if (mode == EntryMode.TRANSFER) {
                TransferAccountsBlock(
                    fromAccount = transferState.fromAccount,
                    toAccount = transferState.toAccount,
                    onFromClick = { showFromPicker = true },
                    onToClick = { showToPicker = true },
                    onSwap = {
                        val from = transferState.fromAccount
                        val to = transferState.toAccount
                        if (from != null && to != null) {
                            transferViewModel.setFromAccount(to)
                            transferViewModel.setToAccount(from)
                        }
                    }
                )

                if (transferState.fromAccount != null &&
                    transferState.fromAccount?.id == transferState.toAccount?.id
                ) {
                    Text(
                        text = stringResource(Res.string.transfer_same_account),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                val fromCurrency = transferState.fromAccount?.currencyCode
                val fromSymbol = fromCurrency?.let {
                    (CurrencyRegistry.symbolFor(it) ?: it)
                } ?: ""
                val fromPreview = run {
                    val decimals = fromCurrency?.let {
                        CurrencyRegistry.decimalsOrFallback(it)
                    } ?: 2
                    val evaluated = Calculator.evaluate(transferState.amountFrom, decimals)
                    if (Calculator.hasOperator(transferState.amountFrom) && evaluated != null) {
                        evaluated.toPlainString()
                    } else null
                }
                // Only the amount leaving the source account gets the big display the other
                // two modes use. The destination amount and the fee are secondary and sit in
                // ordinary fields: three 56sp numbers do not fit on a phone, and with the
                // system keyboard every field can hold focus on its own - which is what the
                // chip row stood in for back when one numpad had to serve all three.
                AmountField(
                    expression = transferState.amountFrom,
                    onExpressionChange = transferViewModel::setAmountFrom,
                    currencySymbol = fromSymbol,
                    previewResult = fromPreview,
                    autoFocus = true,
                    onImeAction = { if (transferState.canSave) transferViewModel.save() },
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (!transferState.sameCurrency && transferState.rate.isNotBlank()) {
                    Text(
                        text = transferState.rate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (!transferState.sameCurrency) {
                    OutlinedTextField(
                        value = transferState.amountTo,
                        onValueChange = transferViewModel::setAmountTo,
                        label = { Text(stringResource(Res.string.transfer_amount_received)) },
                        suffix = { Text(transferState.toAccount?.currencyCode.orEmpty()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                if (showFee) {
                    OutlinedTextField(
                        value = transferState.feeFrom,
                        onValueChange = transferViewModel::setFee,
                        label = { Text(stringResource(Res.string.transfer_fee)) },
                        suffix = { Text(fromCurrency.orEmpty()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                } else {
                    TextButton(
                        onClick = { showFee = true },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(stringResource(Res.string.transfer_add_fee))
                    }
                }
            } else {
                // Account selector — opens a dedicated full-screen picker
                SelectedAccountBar(
                    account = state.selectedAccount,
                    onClick = { onPickAccount(state.selectedAccount?.id) }
                )

                val currencySymbol = state.currencyCode?.let {
                    (CurrencyRegistry.symbolFor(it) ?: it)
                } ?: ""
                AmountField(
                    expression = state.expression,
                    onExpressionChange = viewModel::setAmountText,
                    currencySymbol = currencySymbol,
                    previewResult = state.previewResult,
                    autoFocus = true,
                    onImeAction = { if (state.canSave) viewModel.submit() },
                    modifier = Modifier.padding(vertical = 8.dp)
                )

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
            }

            // Optional note
            if (showNoteInput) {
                val noteValue = if (mode == EntryMode.TRANSFER) transferState.note else state.note
                OutlinedTextField(
                    value = noteValue,
                    onValueChange = {
                        if (mode == EntryMode.TRANSFER) transferViewModel.setNote(it)
                        else viewModel.setNote(it)
                    },
                    placeholder = { Text(stringResource(Res.string.quickadd_note_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true
                )
            }
            } // end scrollable middle region

            HorizontalDivider()

            // What the numpad's DONE key used to be. Pinned below the scrollable region so it
            // stays put, and the whole column is imePadded so the keyboard cannot cover it.
            Button(
                onClick = {
                    if (mode == EntryMode.TRANSFER) transferViewModel.save() else viewModel.submit()
                },
                enabled = if (mode == EntryMode.TRANSFER) transferState.canSave else state.canSave,
                shape = PmButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(Res.string.action_save))
            }
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

        if (showFromPicker) {
            AccountPickerSheet(
                accounts = transferState.accounts,
                selectedId = transferState.fromAccount?.id,
                onPick = { transferViewModel.setFromAccount(it); showFromPicker = false },
                onDismiss = { showFromPicker = false }
            )
        }
        if (showToPicker) {
            AccountPickerSheet(
                accounts = transferState.accounts,
                selectedId = transferState.toAccount?.id,
                onPick = { transferViewModel.setToAccount(it); showToPicker = false },
                onDismiss = { showToPicker = false }
            )
        }
    }
}

@Composable
private fun TransferAccountsBlock(
    fromAccount: Account?,
    toAccount: Account?,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onSwap: () -> Unit
) {
    Text(
        text = stringResource(Res.string.transfer_from),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    SelectedAccountBar(account = fromAccount, onClick = onFromClick)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onSwap) {
            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(Res.string.a11y_swap_accounts))
        }
    }
    Text(
        text = stringResource(Res.string.transfer_to),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    SelectedAccountBar(account = toAccount, onClick = onToClick)
}

@Composable
private fun CategoriesRow(
    categories: List<com.personalmoney.core.domain.model.Category>,
    recentIds: List<Long>,
    selectedId: Long?,
    onSelect: (com.personalmoney.core.domain.model.Category) -> Unit
) {
    // Ordered: selected first, then recents, then the rest
    val ordered = remember(categories, recentIds, selectedId) {
        val orderMap = recentIds.withIndex().associate { (i, id) -> id to i }
        categories.sortedWith(
            compareByDescending<com.personalmoney.core.domain.model.Category> { it.id == selectedId }
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
