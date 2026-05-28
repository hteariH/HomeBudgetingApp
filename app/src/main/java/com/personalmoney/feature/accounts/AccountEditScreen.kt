package com.personalmoney.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.core.ui.components.ConfirmDialog
import com.personalmoney.core.ui.components.IconCircle
import com.personalmoney.core.ui.icons.IconLibrary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) stringResource(R.string.action_edit)
                        else stringResource(R.string.accounts_new)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { viewModel.setArchived(!state.archived) }) {
                            Icon(
                                imageVector = if (state.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                contentDescription = stringResource(R.string.action_archive)
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.accounts_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            CurrencyDropdown(
                selected = state.currencyCode,
                currencies = state.availableCurrencies.map { it.code },
                onPick = viewModel::setCurrency
            )

            OutlinedTextField(
                value = state.initialBalance,
                onValueChange = viewModel::setInitialBalance,
                label = { Text(stringResource(R.string.accounts_initial_balance)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.accounts_icon), style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(IconLibrary.pickerSet) { key ->
                    val selected = state.icon == key
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.setIcon(key) }
                            .then(
                                if (selected) Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconCircle(iconKey = key, color = Color(state.color), size = 40.dp)
                    }
                }
            }

            Text(stringResource(R.string.accounts_color), style = MaterialTheme.typography.titleSmall)
            ColorPickerRow(selected = state.color, onPick = viewModel::setColor)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.accounts_total),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = state.includeInTotal, onCheckedChange = viewModel::setIncludeInTotal)
            }
        }

        if (showDeleteConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.action_delete),
                message = stringResource(R.string.accounts_name) + ": " + state.name,
                confirmLabel = stringResource(R.string.action_delete),
                dismissLabel = stringResource(R.string.action_cancel),
                destructive = true,
                onConfirm = viewModel::delete,
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    selected: String,
    currencies: List<String>,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.accounts_currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = { onPick(code); expanded = false }
                )
            }
        }
    }
}

private val palette = listOf(
    0xFF3D4499L, 0xFF6366F1L, 0xFF3E63DDL, 0xFF06B6D4L, 0xFF008080L,
    0xFF30A46CL, 0xFFFFCC00L, 0xFFFF9F1CL, 0xFFE5484DL, 0xFFEC4899L,
    0xFFAB47BCL, 0xFF8B4513L, 0xFF6B7280L, 0xFF94A3B8L
)

@Composable
private fun ColorPickerRow(selected: Long, onPick: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(palette) { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .clickable { onPick(color) }
                    .then(
                        if (color == selected) Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (color == selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}
