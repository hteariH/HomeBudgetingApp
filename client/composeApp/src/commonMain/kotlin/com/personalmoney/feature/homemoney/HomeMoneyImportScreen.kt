package com.personalmoney.feature.homemoney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.domain.formatMediumDate
import com.personalmoney.core.domain.model.HomeMoneyAccountMapping
import com.personalmoney.core.domain.model.HomeMoneyCategoryMapping
import com.personalmoney.core.domain.model.HomeMoneyImportPlan
import com.personalmoney.core.domain.model.HomeMoneyImportProgress
import com.personalmoney.core.ui.PickerField
import com.personalmoney.core.ui.PickerOption
import com.personalmoney.core.ui.pageBarColors
import com.personalmoney.core.ui.rememberPageNavigator
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Bringing a ledger over from homemoney.ua.
 *
 * Three steps down the page rather than a wizard with a back button: what the user is
 * deciding in the middle step - which HomeMoney account is which of theirs - only makes
 * sense next to what is connected above it and what is about to run below it, and a
 * wizard would hide two thirds of that at any moment.
 *
 * The mapping is the whole screen, and it is deliberately editable rather than a summary.
 * Auto-matching by name gets most of it right and is wrong in exactly the case that
 * matters most: an account the user already keeps by hand under a slightly different name,
 * which a silent import would duplicate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMoneyImportScreen(
    onBack: (() -> Unit)? = null,
    viewModel: HomeMoneyImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigate = rememberPageNavigator()

    // Consent happens on homemoney.ua, so the app leaves the page and the server sends it
    // back. Cleared immediately so a recomposition cannot navigate a second time.
    LaunchedEffect(state.authorizationUrl) {
        state.authorizationUrl?.let {
            viewModel.onAuthorizationOpened()
            navigate(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = pageBarColors(),
                title = { Text(stringResource(Res.string.homemoney_title)) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { ErrorBanner(it, onDismiss = viewModel::clearError) }

            ConnectionCard(state, viewModel)

            if (state.connection.connected) {
                state.plan?.let { plan ->
                    PlanSummaryCard(plan, state.progress, viewModel)
                    AccountsCard(plan, state, viewModel)
                    CategoriesCard(plan, state, viewModel)
                }
            }

            if (state.progress.state != HomeMoneyImportProgress.State.IDLE) {
                ProgressCard(state.progress)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                message,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
        }
    }
}

// ── Step one: the connection ────────────────────────────────────────────────

@Composable
private fun ConnectionCard(state: HomeMoneyImportUiState, viewModel: HomeMoneyImportViewModel) {
    var confirmDisconnect by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.homemoney_step_connect),
                style = MaterialTheme.typography.titleMedium,
            )

            if (!state.connection.connected) {
                Text(
                    stringResource(Res.string.homemoney_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::connect, enabled = !state.busy) {
                    Text(stringResource(Res.string.homemoney_connect))
                }
                return@Column
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(
                        Res.string.homemoney_connected_as,
                        state.connection.label.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (state.connection.invalid) {
                // The stored token stopped working. Saying so is the only way the user
                // knows that reconnecting is the fix rather than retrying.
                Text(
                    stringResource(Res.string.homemoney_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::loadPlan,
                    enabled = !state.busy && !state.progress.running,
                ) {
                    Text(stringResource(Res.string.homemoney_load_plan))
                }
                OutlinedButton(
                    onClick = { confirmDisconnect = true },
                    enabled = !state.busy && !state.progress.running,
                ) {
                    Text(stringResource(Res.string.homemoney_disconnect))
                }
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(Res.string.homemoney_disconnect)) },
            text = { Text(stringResource(Res.string.homemoney_confirm_disconnect)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnect()
                }) { Text(stringResource(Res.string.homemoney_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

// ── Step two: what would happen ─────────────────────────────────────────────

@Composable
private fun PlanSummaryCard(
    plan: HomeMoneyImportPlan,
    progress: HomeMoneyImportProgress,
    viewModel: HomeMoneyImportViewModel,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.homemoney_step_review),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    Res.string.homemoney_summary,
                    plan.accounts.count { it.included },
                    plan.categories.size,
                    plan.transactionCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (plan.firstDate != null && plan.lastDate != null) {
                Text(
                    stringResource(
                        Res.string.homemoney_range,
                        formatMediumDate(plan.firstDate),
                        formatMediumDate(plan.lastDate),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The free plan's cap, and anything else the server refuses up front. Said in
            // the server's own words: it names the limit and what to do about it.
            plan.blockedReason?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (plan.unresolvedCurrencies.isNotEmpty()) {
                Text(
                    stringResource(
                        Res.string.homemoney_currency_needed,
                        plan.unresolvedCurrencies.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::start,
                enabled = plan.canStart && !progress.running,
            ) {
                Text(stringResource(Res.string.homemoney_start))
            }
            Text(
                stringResource(Res.string.homemoney_rerun_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountsCard(
    plan: HomeMoneyImportPlan,
    state: HomeMoneyImportUiState,
    viewModel: HomeMoneyImportViewModel,
) {
    val createNew = stringResource(Res.string.homemoney_create_new)
    val accountOptions = remember(state.accounts, createNew) {
        listOf(PickerOption(null, createNew)) +
            state.accounts.map { PickerOption(it.id, "${it.name} · ${it.currencyCode}") }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.homemoney_accounts_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(Res.string.homemoney_accounts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            plan.accounts.forEachIndexed { index, account ->
                if (index > 0) HorizontalDivider()
                AccountRow(account, accountOptions, state, viewModel)
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: HomeMoneyAccountMapping,
    accountOptions: List<PickerOption>,
    state: HomeMoneyImportUiState,
    viewModel: HomeMoneyImportViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = account.included,
                onCheckedChange = { viewModel.setAccountIncluded(account.externalId, it) },
                enabled = !state.progress.running,
            )
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${account.balance} ${account.currencyCode ?: account.currencyLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!account.included) return@Column

        PickerField(
            label = stringResource(Res.string.homemoney_target_account),
            value = accountOptions.firstOrNull { it.id == account.targetAccountId }?.label,
            options = accountOptions,
            onSelect = { viewModel.setAccountTarget(account.externalId, it) },
            enabled = !state.progress.running,
        )

        // Only when a new account is being created and HomeMoney's label matched nothing.
        // Mapping onto an existing account settles the currency: it is that account's.
        if (account.needsCurrency) {
            val options = remember(state.currencies) {
                state.currencies.map { PickerOption(it.serverId, "${it.code} · ${it.name}") }
            }
            Text(
                stringResource(Res.string.homemoney_currency_unknown, account.currencyLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            PickerField(
                label = stringResource(Res.string.homemoney_choose_currency),
                value = null,
                options = options,
                onSelect = { serverId ->
                    viewModel.setAccountCurrency(
                        account.externalId,
                        state.currencies.firstOrNull { it.serverId == serverId }?.code,
                    )
                },
                enabled = !state.progress.running,
            )
        } else if (account.targetAccountId == null) {
            Text(
                stringResource(Res.string.homemoney_will_create, account.currencyCode.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoriesCard(
    plan: HomeMoneyImportPlan,
    state: HomeMoneyImportUiState,
    viewModel: HomeMoneyImportViewModel,
) {
    val createNew = stringResource(Res.string.homemoney_create_new)

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.homemoney_categories_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            Res.string.homemoney_categories_summary,
                            plan.categories.size,
                            plan.newCategoryCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = viewModel::toggleCategories) {
                    Text(
                        stringResource(
                            if (state.categoriesExpanded) Res.string.homemoney_hide_categories
                            else Res.string.homemoney_review_categories
                        )
                    )
                }
            }

            // Collapsed by default. A HomeMoney tree routinely runs to dozens of
            // categories, and every one of them auto-matched or set to "create new"
            // already - unfolding that by default would bury the accounts above it.
            if (!state.categoriesExpanded) return@Column

            val categoryOptions = state.categories.associate { category ->
                category.type to state.categories.filter { it.type == category.type }
            }

            plan.categories.forEach { category ->
                val options = remember(state.categories, category.type, createNew) {
                    listOf(PickerOption(null, createNew)) +
                        (categoryOptions[category.type].orEmpty())
                            .map { PickerOption(it.id, it.name) }
                }
                CategoryRow(category, options, state, viewModel)
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: HomeMoneyCategoryMapping,
    options: List<PickerOption>,
    state: HomeMoneyImportUiState,
    viewModel: HomeMoneyImportViewModel,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // The full name, not the leaf: "Fuel" under Car and "Fuel" under Boat are two
            // different categories and the leaf alone cannot tell them apart.
            Text(category.fullName, style = MaterialTheme.typography.bodyMedium)
        }
        PickerField(
            label = stringResource(Res.string.homemoney_target_category),
            value = options.firstOrNull { it.id == category.targetCategoryId }?.label,
            options = options,
            onSelect = { viewModel.setCategoryTarget(category.externalId, it) },
            enabled = !state.progress.running,
            modifier = Modifier.width(220.dp),
        )
    }
}

// ── Step three: watching it run ─────────────────────────────────────────────

@Composable
private fun ProgressCard(progress: HomeMoneyImportProgress) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.homemoney_step_import),
                style = MaterialTheme.typography.titleMedium,
            )

            when (progress.state) {
                HomeMoneyImportProgress.State.RUNNING -> {
                    // Indeterminate on purpose: HomeMoney cannot say how far back a
                    // history goes, so the total is only known once the walk has finished.
                    // A bar that guessed would be a bar that jumps.
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        stringResource(Res.string.homemoney_running, progress.processedChunks),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HomeMoneyImportProgress.State.DONE -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(Res.string.homemoney_done),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HomeMoneyImportProgress.State.FAILED -> Text(
                    progress.error ?: stringResource(Res.string.common_request_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                HomeMoneyImportProgress.State.IDLE -> Unit
            }

            Text(
                stringResource(
                    Res.string.homemoney_counts,
                    progress.accountsCreated,
                    progress.categoriesCreated,
                    progress.imported,
                    progress.skipped,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(0.dp))
        }
    }
}
