package com.personalmoney.feature.transfers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.personalmoney.feature.quickadd.components.AccountPickerSheet
import com.personalmoney.feature.quickadd.components.SelectedAccountBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showFrom by remember { mutableStateOf(false) }
    var showTo by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            snackbar.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.transfer_from),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            SelectedAccountBar(account = state.fromAccount, onClick = { showFrom = true })

            OutlinedTextField(
                value = state.amountFrom,
                onValueChange = viewModel::setAmountFrom,
                label = { Text(stringResource(R.string.transfer_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = {
                        val from = state.fromAccount
                        val to = state.toAccount
                        if (from != null && to != null) {
                            viewModel.setFromAccount(to)
                            viewModel.setToAccount(from)
                        }
                    }
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = null)
                }
            }

            Text(
                text = stringResource(R.string.transfer_to),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            SelectedAccountBar(account = state.toAccount, onClick = { showTo = true })

            if (!state.sameCurrency) {
                OutlinedTextField(
                    value = state.amountTo,
                    onValueChange = viewModel::setAmountTo,
                    label = { Text(stringResource(R.string.transfer_amount) + " (${state.toAccount?.currencyCode ?: ""})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }

            OutlinedTextField(
                value = state.feeFrom,
                onValueChange = viewModel::setFee,
                label = { Text(stringResource(R.string.transfer_fee)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.quickadd_note)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            if (state.fromAccount?.id == state.toAccount?.id && state.fromAccount != null) {
                Text(
                    text = stringResource(R.string.transfer_same_account),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (showFrom) {
            AccountPickerSheet(
                accounts = state.accounts,
                selectedId = state.fromAccount?.id,
                onPick = { viewModel.setFromAccount(it); showFrom = false },
                onDismiss = { showFrom = false }
            )
        }
        if (showTo) {
            AccountPickerSheet(
                accounts = state.accounts,
                selectedId = state.toAccount?.id,
                onPick = { viewModel.setToAccount(it); showTo = false },
                onDismiss = { showTo = false }
            )
        }
    }
}
