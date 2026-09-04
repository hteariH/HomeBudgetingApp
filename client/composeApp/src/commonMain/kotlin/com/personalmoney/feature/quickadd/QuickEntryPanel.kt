package com.personalmoney.feature.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.formatMediumDate
import com.personalmoney.core.ui.PageWidth
import com.personalmoney.core.ui.PickerField
import com.personalmoney.core.ui.PickerOption
import com.personalmoney.core.ui.theme.LocalFinanceColors
import com.personalmoney.core.ui.theme.PmButtonShape
import com.personalmoney.feature.quickadd.components.DatePickerDialogWrapper
import com.personalmoney.feature.transfers.TransferUiState
import com.personalmoney.feature.transfers.TransferViewModel
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entering a transaction without leaving the page.
 *
 * The phone flow is a full screen built around one oversized amount, which is right for a
 * thumb and wrong for a desk: on a desktop the fields are all visible at once and tabbing
 * between them beats a screen you have to scroll. So this is the same view models and the
 * same validation, in the shape a wide window wants - the difference is the layout, not the
 * feature, which is why it is chosen by window width rather than by platform.
 *
 * All three kinds of entry, because a transfer between your own accounts is one of them and
 * hiding it behind a different screen made it look like something rarer than it is. Expense
 * and income come from [QuickAddViewModel]; a transfer is a pair of linked rows with its own
 * rules - two accounts, an optional fee, a rate when the currencies differ - so it keeps its
 * own [TransferViewModel], exactly as the phone screen does.
 *
 * Both shapes type the amount into the same `setAmountText`, so there is one input path to
 * reason about rather than one per platform.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEntryPanel(
    modifier: Modifier = Modifier,
    viewModel: QuickAddViewModel = koinViewModel(),
    transferViewModel: TransferViewModel = koinViewModel(),
    onSaved: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transfer by transferViewModel.uiState.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(EntryMode.EXPENSE) }

    // The panel stays put and clears itself, rather than navigating away: entering several
    // transactions in a row is the thing a desktop user is most likely to be doing.
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(transfer.saved) { if (transfer.saved) onSaved() }

    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val finance = LocalFinanceColors.current
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(
                    Triple(EntryMode.EXPENSE, Res.string.dashboard_add_expense, finance.expense),
                    Triple(EntryMode.INCOME, Res.string.dashboard_add_income, finance.income),
                    Triple(EntryMode.TRANSFER, Res.string.transfer_mode, finance.transfer),
                )
                options.forEachIndexed { index, (option, label, tint) ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = {
                            mode = option
                            // Expense and income are one view model in two states; a
                            // transfer is a different one, so it needs no telling.
                            when (option) {
                                EntryMode.EXPENSE -> viewModel.setType(TransactionType.EXPENSE)
                                EntryMode.INCOME -> viewModel.setType(TransactionType.INCOME)
                                EntryMode.TRANSFER -> Unit
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        // The same three colours the amounts wear everywhere else, so the
                        // tab and the row it will produce mean the same thing. A tint plus
                        // coloured text rather than a solid slab: at full strength three
                        // saturated blocks fight the card they sit on, and the selected
                        // one would shout louder than the amount being typed.
                        //
                        // The label and the tick carry the state on their own; the colour
                        // only agrees with them, which is what keeps this readable when
                        // red and green are the two a viewer cannot tell apart.
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = tint.copy(alpha = 0.14f),
                            activeContentColor = tint,
                            activeBorderColor = tint,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) { Text(stringResource(label)) }
                }
            }

            if (mode == EntryMode.TRANSFER) {
                TransferFields(transfer, transferViewModel)
            } else {
                LedgerFields(state, viewModel)
            }
        }
    }
}

/** Expense and income: one account, a category, an amount and a date. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LedgerFields(state: QuickAddUiState, viewModel: QuickAddViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }

    // Wraps instead of squashing: below roughly a tablet the four controls stack, above it
    // they sit on one line the way the fields do in a desktop form.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PickerField(
            label = stringResource(Res.string.common_account),
            value = state.selectedAccount?.name,
            options = state.accounts.map { PickerOption(it.id, it.name) },
            onSelect = { id -> id?.let(viewModel::selectAccountById) },
            modifier = Modifier.widthIn(min = 200.dp, max = 260.dp),
        )
        PickerField(
            label = stringResource(Res.string.common_category),
            value = state.selectedCategory?.name,
            options = state.categories.map { PickerOption(it.id, it.name) },
            onSelect = { id ->
                state.categories.firstOrNull { it.id == id }?.let(viewModel::selectCategory)
            },
            modifier = Modifier.widthIn(min = 200.dp, max = 260.dp),
        )
        OutlinedTextField(
            value = state.expression,
            onValueChange = viewModel::setAmountText,
            label = { Text(stringResource(Res.string.common_amount)) },
            suffix = { state.selectedAccount?.let { Text(it.currencyCode) } },
            singleLine = true,
            isError = state.expression.isNotBlank() && !state.isAmountValid,
            // Decimal, not number: the expression accepts operators as well as digits.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.widthIn(min = 140.dp, max = 200.dp),
        )
        TextButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.widthIn(min = 140.dp),
        ) { Text(formatMediumDate(state.date)) }
    }

    OutlinedTextField(
        value = state.note,
        onValueChange = viewModel::setNote,
        label = { Text(stringResource(Res.string.quickadd_note)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().widthIn(max = PageWidth.Form),
    )

    SaveRow(error = state.error, enabled = state.canSave && !state.saving, onSave = viewModel::submit)

    if (showDatePicker) {
        DatePickerDialogWrapper(
            initial = state.date,
            onConfirm = {
                viewModel.setDate(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * A transfer: two accounts and what leaves the first.
 *
 * The destination amount appears only when the currencies differ, because that is the only
 * time it is a separate number - and then the rate the app would use is shown beside it, so
 * a wrong-looking conversion is visible before saving rather than after.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TransferFields(state: TransferUiState, viewModel: TransferViewModel) {
    var showFee by remember { mutableStateOf(false) }
    val options = state.accounts.map { PickerOption(it.id, it.name) }

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PickerField(
            label = stringResource(Res.string.transfer_from),
            value = state.fromAccount?.name,
            options = options,
            onSelect = { id ->
                state.accounts.firstOrNull { it.id == id }?.let(viewModel::setFromAccount)
            },
            modifier = Modifier.widthIn(min = 190.dp, max = 240.dp),
        )
        IconButton(
            onClick = {
                val from = state.fromAccount
                val to = state.toAccount
                if (from != null && to != null) {
                    viewModel.setFromAccount(to)
                    viewModel.setToAccount(from)
                }
            },
            enabled = state.fromAccount != null && state.toAccount != null,
        ) {
            Icon(
                Icons.Filled.SwapHoriz,
                contentDescription = stringResource(Res.string.transfer_mode),
            )
        }
        PickerField(
            label = stringResource(Res.string.transfer_to),
            value = state.toAccount?.name,
            options = options,
            onSelect = { id ->
                state.accounts.firstOrNull { it.id == id }?.let(viewModel::setToAccount)
            },
            modifier = Modifier.widthIn(min = 190.dp, max = 240.dp),
        )
    }

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.amountFrom,
            onValueChange = viewModel::setAmountFrom,
            // Just "Amount" while there is only one; the pair is only worth
            // distinguishing when a second field is beside it.
            label = {
                Text(
                    stringResource(
                        if (state.sameCurrency) Res.string.transfer_amount
                        else Res.string.transfer_amount_from
                    )
                )
            },
            suffix = { state.fromAccount?.let { Text(it.currencyCode) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.widthIn(min = 150.dp, max = 200.dp),
        )

        if (!state.sameCurrency) {
            OutlinedTextField(
                value = state.amountTo,
                onValueChange = viewModel::setAmountTo,
                label = { Text(stringResource(Res.string.transfer_amount_to)) },
                suffix = { state.toAccount?.let { Text(it.currencyCode) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.widthIn(min = 150.dp, max = 200.dp),
            )
        }

        if (showFee) {
            OutlinedTextField(
                value = state.feeFrom,
                onValueChange = viewModel::setFee,
                label = { Text(stringResource(Res.string.transfer_fee)) },
                suffix = { state.fromAccount?.let { Text(it.currencyCode) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.widthIn(min = 130.dp, max = 180.dp),
            )
        } else {
            // Hidden until asked for: most transfers have no fee, and an always-present
            // zero is one more field to skip past every time.
            TextButton(onClick = { showFee = true }) {
                Text(stringResource(Res.string.transfer_add_fee))
            }
        }
    }

    if (!state.sameCurrency && state.rate.isNotBlank()) {
        Text(
            state.rate,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    OutlinedTextField(
        value = state.note,
        onValueChange = viewModel::setNote,
        label = { Text(stringResource(Res.string.quickadd_note)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().widthIn(max = PageWidth.Form),
    )

    SaveRow(error = state.error, enabled = state.canSave, onSave = viewModel::save)
}

/** The error and the button, identical for both kinds of entry. */
@Composable
private fun SaveRow(error: String?, enabled: Boolean, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(8.dp))
        Button(onClick = onSave, enabled = enabled, shape = PmButtonShape) {
            Text(stringResource(Res.string.action_save))
        }
    }
}
