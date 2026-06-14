package com.personalmoney.feature.homemoney

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMoneyImportScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: HomeMoneyImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Mirror the toolbar back affordance so the hardware/gesture back follows the same step logic
    // instead of popping the whole screen and discarding the fetched accounts.
    BackHandler(enabled = state.step != ImportStep.CREDENTIALS) {
        when (state.step) {
            ImportStep.PREVIEW -> viewModel.backToCredentials()
            ImportStep.DONE -> onDone()
            ImportStep.CREDENTIALS -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.homemoney_import_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (state.step) {
                                ImportStep.PREVIEW -> viewModel.backToCredentials()
                                ImportStep.DONE -> onDone()
                                ImportStep.CREDENTIALS -> onBack()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.step) {
                ImportStep.CREDENTIALS -> CredentialsStep(state, viewModel)
                ImportStep.PREVIEW -> PreviewStep(state, viewModel)
                ImportStep.DONE -> DoneStep(state, onDone)
            }
        }
    }
}

@Composable
private fun CredentialsStep(
    state: HomeMoneyImportUiState,
    viewModel: HomeMoneyImportViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.homemoney_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::setUsername,
            label = { Text(stringResource(R.string.homemoney_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text(stringResource(R.string.homemoney_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.error != null) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = viewModel::connect,
            enabled = state.canConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.homemoney_connect))
            }
        }
    }
}

@Composable
private fun PreviewStep(
    state: HomeMoneyImportUiState,
    viewModel: HomeMoneyImportViewModel,
) {
    if (state.rows.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.homemoney_no_accounts),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        state.accountHolder?.let { holder ->
            Text(
                text = stringResource(R.string.homemoney_signed_in_as, holder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        val allSelected = state.rows.all { it.selected }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.homemoney_found, state.rows.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.setAllSelected(!allSelected) }) {
                Text(
                    stringResource(
                        if (allSelected) R.string.homemoney_deselect_all else R.string.homemoney_select_all
                    )
                )
            }
        }
        HorizontalDivider()

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(state.rows) { index, row ->
                CandidateRowItem(row = row, onToggle = { viewModel.toggle(index) })
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        if (state.error != null) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Button(
            onClick = viewModel::import,
            enabled = state.canImport,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.homemoney_import_button, state.selectedCount))
            }
        }
    }
}

@Composable
private fun CandidateRowItem(
    row: CandidateRow,
    onToggle: () -> Unit,
) {
    val balance = remember(row.candidate.balanceMinor, row.candidate.currencyCode) {
        MoneyFormatter.format(
            Money(row.candidate.balanceMinor, CurrencyCode(row.candidate.currencyCode))
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.selected, onCheckedChange = { onToggle() })
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.candidate.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                append(balance)
                row.candidate.groupName?.let { append("  •  ").append(it) }
                if (row.candidate.alreadyExists) {
                    append("  •  ").append(stringResource(R.string.homemoney_already_exists))
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DoneStep(
    state: HomeMoneyImportUiState,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.homemoney_done, state.importedCount),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) {
            Text(stringResource(R.string.homemoney_done_button))
        }
    }
}
