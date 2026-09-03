package com.personalmoney.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.personalmoney.core.ui.PageContainer
import com.personalmoney.core.ui.PageWidth
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    if (state.showAllCurrenciesDialog) {
        AllCurrenciesDialog(
            currencies = state.filteredAllCurrencies,
            searchQuery = state.currencySearchQuery,
            onSearchChange = viewModel::setCurrencySearchQuery,
            onSelect = viewModel::selectCurrencyFromAll,
            onDismiss = viewModel::dismissAllCurrenciesDialog
        )
    }

    Scaffold { padding ->
        // A phone-shaped wizard: title at the top, button under the thumb. Stretched across
        // a browser window it put the heading against one edge and the button against both,
        // which is the first thing a new account now sees on the web. Capping it to the form
        // width is a no-op on a phone and makes it a wizard again in a browser.
        PageContainer(
            modifier = Modifier.fillMaxSize().padding(padding),
            maxWidth = PageWidth.Form,
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Progress
                val progress = (state.step + 1).toFloat() / state.totalSteps.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(Res.string.onboarding_step_of, state.step + 1, state.totalSteps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(32.dp))

                Box(modifier = Modifier.weight(1f)) {
                    when (state.step) {
                        0 -> WelcomeStep()
                        1 -> CurrencyStep(
                            currencies = state.currencies,
                            selectedCode = state.selectedCurrencyCode,
                            onSelect = viewModel::selectCurrency,
                            onNotMyList = viewModel::showAllCurrenciesDialog
                        )
                        2 -> AccountStep(
                            name = state.accountName,
                            onNameChange = viewModel::setAccountName
                        )
                    }
                }
            }

            // Bottom navigation
            Column {
                // Above the button, because that is what the user just pressed and is
                // waiting on. Onboarding cannot be navigated away from, so a failure here
                // has to say something rather than leave the screen looking idle.
                state.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
                Button(
                    onClick = {
                        if (state.step < state.totalSteps - 1) viewModel.next()
                        else viewModel.finish()
                    },
                    enabled = state.canProceed && !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (state.step < state.totalSteps - 1)
                            stringResource(Res.string.onboarding_next)
                        else
                            stringResource(Res.string.onboarding_start)
                    )
                }
                if (state.step > 0) {
                    TextButton(
                        onClick = viewModel::skip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.onboarding_skip))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(Res.string.onboarding_welcome),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(Res.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CurrencyStep(
    currencies: List<com.personalmoney.core.domain.model.Currency>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onNotMyList: () -> Unit
) {
    val popularCodes = remember {
        listOf("USD", "EUR", "GBP", "RUB", "UAH", "PLN", "CZK", "HUF", "TRY",
               "CNY", "JPY", "KRW", "INR", "BRL", "CAD", "AUD", "CHF", "SEK",
               "NOK", "DKK", "SGD", "HKD", "AED", "ILS", "MXN", "ZAR")
    }

    val sortedCurrencies = remember(currencies) {
        val popularList = popularCodes.mapNotNull { code -> currencies.find { it.code == code } }
        val rest = currencies.filter { it.code !in popularCodes }
        popularList + rest
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_pick_currency),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(sortedCurrencies, key = { it.code }) { currency ->
                CurrencyChip(currency, currency.code == selectedCode, onSelect)
            }
            item {
                TextButton(
                    onClick = onNotMyList,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(stringResource(Res.string.onboarding_currency_not_here))
                }
            }
        }
    }
}

@Composable
private fun CurrencyChip(
    currency: com.personalmoney.core.domain.model.Currency,
    selected: Boolean,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(currency.code) },
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currency.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.size(28.dp),
                    fontWeight = FontWeight.Bold
                )
                Column {
                    Text(currency.code, style = MaterialTheme.typography.titleSmall)
                    Text(
                        currency.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AllCurrenciesDialog(
    currencies: List<com.personalmoney.core.domain.model.Currency>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.onboarding_all_currencies)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(stringResource(Res.string.currencies_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(currencies, key = { it.code }) { currency ->
                        CurrencyChip(currency, false, onSelect)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
private fun AccountStep(
    name: String,
    onNameChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.onboarding_first_account),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.onboarding_account_name)) },
            placeholder = { Text(stringResource(Res.string.onboarding_account_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
