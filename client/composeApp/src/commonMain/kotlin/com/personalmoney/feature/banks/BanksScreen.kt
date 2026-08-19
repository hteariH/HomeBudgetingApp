package com.personalmoney.feature.banks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.formatMediumDate
import com.personalmoney.core.domain.model.BankConnection
import com.personalmoney.core.domain.model.BankConnectionStatus
import com.personalmoney.core.domain.model.BankProvider
import com.personalmoney.core.domain.model.PendingImport
import com.personalmoney.core.domain.model.RemoteAccount
import com.personalmoney.core.ui.PageWidth
import com.personalmoney.core.ui.PickerField
import com.personalmoney.core.ui.PickerOption
import com.personalmoney.core.ui.components.MoneyText
import com.personalmoney.feature.quickadd.components.DatePickerDialogWrapper
import com.personalmoney.feature.settings.rememberUrlOpener
import com.personalmoney.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Bank connections and statement import.
 *
 * The React page this replaces (`frontend/src/pages/Banks.tsx`) drew three HTML tables. A
 * table is the wrong unit here: the same markup has to survive a browser window being
 * dragged narrow, and columns that cannot wrap turn into a horizontal scrollbar. So each
 * row is a card that reflows - the bank's name and balance stay together, and the control
 * that acts on it moves under them rather than off the edge.
 *
 * Nothing on this screen knows which banks exist; see [BanksViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanksScreen(
    onBack: () -> Unit,
    viewModel: BanksViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.banks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            Text(
                pluralStringResource(
                    Res.plurals.banks_connection_count,
                    state.connections.size,
                    state.connections.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.error?.let { ErrorBanner(it, onDismiss = viewModel::clearError) }

            ProvidersCard(state, onConnect = viewModel::openConnect)

            state.connections.forEach { connection ->
                ConnectionCard(connection, state, viewModel)
            }

            if (state.linkedAccounts.isNotEmpty()) {
                ImportCard(state, viewModel)
            }

            // Always offered, unlike the bank import above: a file needs no connection,
            // which is the whole point of it - it reaches the banks with no API at all.
            CsvImportCard(
                accounts = state.accounts,
                categories = state.categories,
                onImported = viewModel::onLedgerChanged,
            )
        }
    }

    state.connect?.let { draft ->
        ConnectDialog(
            draft = draft,
            onValueChange = viewModel::setCredential,
            onSubmit = viewModel::submitConnect,
            onDismiss = viewModel::closeConnect,
        )
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

/** One button per bank the server can talk to. The list is data, not a hardcoded menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProvidersCard(
    state: BanksUiState,
    onConnect: (BankProvider) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.banks_add_connection),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.providers.isEmpty()) {
                Text(
                    stringResource(Res.string.banks_no_providers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.providers.forEach { provider ->
                        OutlinedButton(onClick = { onConnect(provider) }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(provider.displayName)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionCard(
    connection: BankConnection,
    state: BanksUiState,
    viewModel: BanksViewModel,
) {
    var confirmingDisconnect by remember { mutableStateOf(false) }
    val expanded = state.openConnectionId == connection.id

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.widthIn(min = 220.dp).weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            connection.providerDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            connection.label ?: stringResource(Res.string.common_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (connection.status == BankConnectionStatus.INVALID) {
                            // Named as well as coloured: this is the one state the user can
                            // act on, and it must not depend on seeing red.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    stringResource(Res.string.banks_credentials_rejected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.verify(connection.id) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(Res.string.banks_verify))
                    }
                    TextButton(onClick = { viewModel.toggleAccounts(connection.id) }) {
                        Icon(Icons.Filled.Link, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(Res.string.banks_accounts))
                    }
                    IconButton(onClick = { confirmingDisconnect = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(Res.string.banks_disconnect),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.size(12.dp))
                HorizontalDivider()
                Spacer(Modifier.size(12.dp))
                if (state.remoteBusy) {
                    Text(
                        stringResource(Res.string.banks_loading_accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(Res.string.banks_at_the_bank),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.remoteAccounts.forEach { remote ->
                            RemoteAccountRow(remote, connection.id, state, viewModel)
                        }
                    }
                }
            }
        }
    }

    if (confirmingDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmingDisconnect = false },
            title = { Text(stringResource(Res.string.banks_disconnect)) },
            text = { Text(stringResource(Res.string.banks_confirm_disconnect)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDisconnect = false
                    viewModel.disconnect(connection.id)
                }) { Text(stringResource(Res.string.banks_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDisconnect = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

/**
 * One account at the bank, and what it is pointed at locally.
 *
 * The link picker offers only accounts in the same currency, which is the server's rule as
 * well: a statement is imported in the account's own units, so linking across currencies
 * would silently rescale somebody's money.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteAccountRow(
    remote: RemoteAccount,
    connectionId: Long,
    state: BanksUiState,
    viewModel: BanksViewModel,
) {
    val linkedTo = remote.linkedAccountId?.let { id -> state.accounts.firstOrNull { it.id == id } }

    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.widthIn(min = 200.dp).weight(1f)) {
            Text(remote.displayName, style = MaterialTheme.typography.bodyLarge)
            MoneyText(
                money = Money(remote.balanceMinor, CurrencyCode(remote.currencyCode)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (linkedTo != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        stringResource(Res.string.banks_linked_to),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(linkedTo.name, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = { viewModel.unlink(connectionId, linkedTo.id) }) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(Res.string.banks_unlink))
                }
            }
        } else {
            PickerField(
                label = stringResource(Res.string.banks_link_to),
                value = null,
                options = state.linkCandidates(remote.currencyCode)
                    .map { PickerOption(it.id, it.name) },
                onSelect = { id ->
                    id?.let { viewModel.link(connectionId, it, remote.externalId) }
                },
                modifier = Modifier.widthIn(min = 200.dp, max = 260.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportCard(state: BanksUiState, viewModel: BanksViewModel) {
    val import = state.import
    var picking by remember { mutableStateOf<DateSlot?>(null) }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.banks_import_title),
                style = MaterialTheme.typography.titleMedium,
            )

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickerField(
                    label = stringResource(Res.string.common_account),
                    value = state.importAccount?.name,
                    options = state.linkedAccounts.map { PickerOption(it.id, it.name) },
                    onSelect = viewModel::setImportAccount,
                    modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
                )
                DateField(
                    label = stringResource(Res.string.common_from),
                    date = import.from,
                    onClick = { picking = DateSlot.FROM },
                )
                DateField(
                    label = stringResource(Res.string.common_to),
                    date = import.to,
                    onClick = { picking = DateSlot.TO },
                )
                Button(
                    onClick = viewModel::preview,
                    enabled = import.accountId != null && !import.busy,
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.banks_preview))
                }
            }

            import.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            import.result?.let { result ->
                Text(
                    stringResource(
                        Res.string.banks_imported,
                        result.imported.toString(),
                        result.skipped.toString(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            import.rows?.let { rows ->
                HorizontalDivider()
                Text(
                    stringResource(
                        Res.string.csv_summary,
                        import.freshCount.toString(),
                        (rows.size - import.freshCount).toString(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rows.forEach { row ->
                    PendingRow(row, state, viewModel)
                }
                Button(
                    onClick = viewModel::confirmImport,
                    enabled = !import.busy && import.freshCount > 0,
                ) {
                    Text(stringResource(Res.string.banks_import_count, import.freshCount))
                }
            }
        }
    }

    picking?.let { slot ->
        DatePickerDialogWrapper(
            initial = if (slot == DateSlot.FROM) import.from else import.to,
            onConfirm = { date ->
                if (slot == DateSlot.FROM) viewModel.setImportFrom(date) else viewModel.setImportTo(date)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** Which end of the range the open date picker is editing. */
private enum class DateSlot { FROM, TO }

@Composable
private fun DateField(label: String, date: LocalDate, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.widthIn(min = 150.dp)) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatMediumDate(date), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * One statement line awaiting a decision.
 *
 * Rows already in the ledger are dimmed rather than hidden, so the count the user sees
 * matches the statement they are looking at in their banking app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingRow(row: PendingImport, state: BanksUiState, viewModel: BanksViewModel) {
    val currency = state.importAccount?.currencyCode ?: return
    val categories = state.categoriesFor(row.type)
    val chosen = state.import.categories[row.externalId]
    val chosenName = categories.firstOrNull { it.id == chosen }?.name

    FlowRow(
        Modifier.fillMaxWidth().alpha(if (row.alreadyImported) 0.45f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.widthIn(min = 200.dp).weight(1f)) {
            Text(
                row.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatMediumDate(row.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MoneyText(
            money = Money(row.amountMinor, CurrencyCode(currency)),
            type = row.type,
            showSign = true,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (row.alreadyImported) {
            Text(
                stringResource(Res.string.csv_already_imported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PickerField(
                label = stringResource(Res.string.common_category),
                value = chosenName ?: stringResource(Res.string.common_uncategorised),
                options = listOf(
                    PickerOption(null, stringResource(Res.string.common_uncategorised)),
                ) + categories.map { PickerOption(it.id, it.name) },
                onSelect = { id -> viewModel.setRowCategory(row.externalId, id) },
                modifier = Modifier.widthIn(min = 180.dp, max = 240.dp),
            )
        }
    }
}

/**
 * The connect form, built from the provider's own field list.
 *
 * There is no per-bank branch anywhere in here. [com.personalmoney.core.domain.model.CredentialField.secret] decides
 * masking, and it comes from the server - which is what lets a new bank ship
 * without a client release.
 */
@Composable
private fun ConnectDialog(
    draft: ConnectDraft,
    onValueChange: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.banks_connect_title, draft.provider.displayName))
        },
        text = {
            Column(
                Modifier.widthIn(max = PageWidth.Form),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                draft.provider.credentialFields.forEach { field ->
                    Column {
                        OutlinedTextField(
                            value = draft.values[field.name].orEmpty(),
                            onValueChange = { onValueChange(field.name, it) },
                            label = { Text(field.label) },
                            placeholder = field.placeholder?.let { { Text(it) } },
                            singleLine = true,
                            visualTransformation = if (field.secret) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (field.secret) KeyboardType.Password else KeyboardType.Text,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        field.helpText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                draft.provider.helpUrl?.let { url ->
                    val open = rememberUrlOpener()
                    TextButton(onClick = { open(url) }) {
                        Text(stringResource(Res.string.banks_where_from))
                    }
                }

                draft.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = draft.canSubmit) {
                Text(
                    stringResource(
                        if (draft.busy) Res.string.banks_checking else Res.string.banks_check_and_connect
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
