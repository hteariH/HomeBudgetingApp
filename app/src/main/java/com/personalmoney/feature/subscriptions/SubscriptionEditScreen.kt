package com.personalmoney.feature.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.core.domain.SubscriptionFrequency
import com.personalmoney.feature.quickadd.components.DatePickerDialogWrapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SubscriptionEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditing) stringResource(R.string.action_edit)
                    else stringResource(R.string.subscriptions_new))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.subscriptions_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                label = { Text("${stringResource(R.string.subscriptions_amount)} (${state.currencyCode})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.subscriptions_frequency), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val freqs = listOf(
                    SubscriptionFrequency.DAILY to R.string.freq_daily,
                    SubscriptionFrequency.WEEKLY to R.string.freq_weekly,
                    SubscriptionFrequency.MONTHLY to R.string.freq_monthly,
                    SubscriptionFrequency.YEARLY to R.string.freq_yearly
                )
                freqs.forEachIndexed { i, (freq, label) ->
                    SegmentedButton(
                        selected = state.frequency == freq,
                        onClick = { viewModel.setFrequency(freq) },
                        shape = SegmentedButtonDefaults.itemShape(i, freqs.size)
                    ) { Text(stringResource(label)) }
                }
            }

            Text("${stringResource(R.string.subscriptions_next_charge)}: ${state.nextChargeDate}", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.action_edit)) }

            if (state.accounts.size > 1) {
                Text(stringResource(R.string.quickadd_account), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.accounts) { acc ->
                        FilterChip(
                            selected = acc.id == state.accountId,
                            onClick = { viewModel.setAccount(acc.id) },
                            label = { Text(acc.name) }
                        )
                    }
                }
            }

            if (state.categories.isNotEmpty()) {
                Text(stringResource(R.string.quickadd_category), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(state.categories) { cat ->
                        FilterChip(
                            selected = cat.id == state.categoryId,
                            onClick = { viewModel.setCategory(if (cat.id == state.categoryId) null else cat.id) },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }
        }

        if (showDatePicker) {
            DatePickerDialogWrapper(
                initial = state.nextChargeDate,
                onConfirm = { viewModel.setNextChargeDate(it); showDatePicker = false },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}
