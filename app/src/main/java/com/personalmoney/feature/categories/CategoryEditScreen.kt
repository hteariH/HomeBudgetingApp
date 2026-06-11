package com.personalmoney.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.R
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.ui.components.IconCircle
import com.personalmoney.core.ui.icons.IconLibrary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditing) stringResource(R.string.action_edit)
                    else stringResource(R.string.categories_new))
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.categories_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(CategoryType.EXPENSE, CategoryType.INCOME)
                options.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = state.type == t,
                        onClick = { viewModel.setType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size)
                    ) {
                        Text(stringResource(
                            if (t == CategoryType.EXPENSE) R.string.categories_expense
                            else R.string.categories_income
                        ))
                    }
                }
            }

            if (state.showBudget) {
                OutlinedTextField(
                    value = state.budgetLimit,
                    onValueChange = viewModel::setBudgetLimit,
                    label = { Text(stringResource(R.string.category_budget)) },
                    suffix = { Text(state.baseCurrency) },
                    supportingText = { Text(stringResource(R.string.category_budget_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconCircle(iconKey = key, color = Color(state.color), size = 40.dp)
                    }
                }
            }

            Text(stringResource(R.string.accounts_color), style = MaterialTheme.typography.titleSmall)
            val palette = remember { listOf(
                0xFF3D4499L, 0xFF6366F1L, 0xFF3E63DDL, 0xFF06B6D4L, 0xFF008080L,
                0xFF30A46CL, 0xFFFFCC00L, 0xFFFF9F1CL, 0xFFE5484DL, 0xFFEC4899L,
                0xFFAB47BCL, 0xFF8B4513L, 0xFF6B7280L, 0xFF94A3B8L
            ) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(palette) { c ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable { viewModel.setColor(c) }
                            .then(
                                if (c == state.color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (c == state.color) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

