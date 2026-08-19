package com.personalmoney.feature.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.formatMediumDate
import com.personalmoney.core.ui.PageWidth
import com.personalmoney.feature.quickadd.components.DatePickerDialogWrapper
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entering a transaction without leaving the dashboard.
 *
 * The phone flow is a full screen with a numpad, which is right for a thumb and wrong for a
 * keyboard: on a desktop the fields are all visible at once and tabbing between them is
 * faster than any custom keypad. So this is the same [QuickAddViewModel] and the same
 * validation, in the shape a wide window wants - the difference is the input device, not the
 * feature, which is why it is chosen by window width rather than by platform.
 *
 * The amount goes through the same expression string the numpad builds, so `12+3.40` works
 * here too.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEntryPanel(
    modifier: Modifier = Modifier,
    viewModel: QuickAddViewModel = koinViewModel(),
    onSaved: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    // The panel stays put and clears itself, rather than navigating away: entering several
    // transactions in a row is the thing a desktop user is most likely to be doing.
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val types = listOf(
                    TransactionType.EXPENSE to Res.string.dashboard_add_expense,
                    TransactionType.INCOME to Res.string.dashboard_add_income,
                )
                types.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    ) { Text(stringResource(label)) }
                }
            }

            // Wraps instead of squashing: below roughly a tablet the four controls stack,
            // above it they sit on one line the way the fields do in a desktop form.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickerField(
                    label = stringResource(Res.string.common_account),
                    value = state.selectedAccount?.name,
                    options = state.accounts.map { it.id to it.name },
                    onSelect = { id -> viewModel.selectAccountById(id) },
                    modifier = Modifier.widthIn(min = 200.dp, max = 260.dp),
                )
                PickerField(
                    label = stringResource(Res.string.common_category),
                    value = state.selectedCategory?.name,
                    options = state.categories.map { it.id to it.name },
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

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSave && !state.saving,
                ) { Text(stringResource(Res.string.action_save)) }
            }
        }
    }

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

/** A read-only field that opens a menu - the desktop equivalent of the phone's picker sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    label: String,
    value: String?,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // An invisible button over the field: a read-only OutlinedTextField still takes the
        // click itself, so the menu would never open.
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.matchParentSize(),
        ) {}
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
