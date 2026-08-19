package com.personalmoney.feature.currencies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.ui.components.ConfirmDialog
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyEditScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CurrencyEditViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    val errMsg = when (state.errorMessage) {
        "CODE_TAKEN" -> stringResource(Res.string.currencies_code_taken)
        "INVALID_CODE" -> stringResource(Res.string.currencies_code_invalid)
        null -> null
        else -> state.errorMessage
    }
    LaunchedEffect(errMsg) {
        errMsg?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (state.usageBlocked > 0) {
        ConfirmDialog(
            title = stringResource(Res.string.action_delete),
            message = stringResource(Res.string.currencies_in_use, state.usageBlocked),
            confirmLabel = stringResource(Res.string.action_archive),
            dismissLabel = stringResource(Res.string.action_cancel),
            destructive = true,
            onConfirm = {
                viewModel.setActive(false)
                viewModel.save()
                viewModel.clearUsageBlocked()
            },
            onDismiss = { viewModel.clearUsageBlocked() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) state.name.ifBlank { state.code }
                        else stringResource(Res.string.currencies_new)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(Res.string.action_delete)
                            )
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::setCode,
                label = { Text(stringResource(Res.string.currencies_code)) },
                singleLine = true,
                enabled = !state.isEditing,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters
                ),
                supportingText = {
                    if (state.code.isNotEmpty() && state.errorMessage == "INVALID_CODE") {
                        Text(stringResource(Res.string.currencies_code_invalid))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(Res.string.currencies_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.symbol,
                onValueChange = viewModel::setSymbol,
                label = { Text(stringResource(Res.string.currencies_symbol)) },
                singleLine = true,
                supportingText = { Text("$ € ¥ ₿ Ξ …") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(Res.string.currencies_decimals),
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (0..6).forEach { n ->
                    FilterChip(
                        selected = state.decimals == n,
                        onClick = { viewModel.setDecimals(n) },
                        label = { Text(n.toString()) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.currencies_active),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.active,
                    onCheckedChange = viewModel::setActive
                )
            }
        }
    }
}
