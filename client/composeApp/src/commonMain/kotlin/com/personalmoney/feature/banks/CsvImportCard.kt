package com.personalmoney.feature.banks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.domain.CategoryType
import com.personalmoney.core.domain.CurrencyCode
import com.personalmoney.core.domain.Money
import com.personalmoney.core.domain.TransactionType
import com.personalmoney.core.domain.formatMediumDate
import com.personalmoney.core.domain.model.Account
import com.personalmoney.core.domain.model.Category
import com.personalmoney.core.domain.model.CsvPreview
import com.personalmoney.core.domain.model.CsvRow
import com.personalmoney.core.ui.PickerField
import com.personalmoney.core.ui.PickerOption
import com.personalmoney.core.ui.components.MoneyText
import com.personalmoney.core.ui.rememberCsvFilePicker
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Import from a statement file.
 *
 * Sits under the bank connections because it is the same decision in the same place -
 * preview, categorise, confirm - reached a different way. It also covers far more banks
 * than any integration will: every bank exports CSV, including the ones with no API a
 * small service can reach.
 *
 * The accounts and categories come from the screen above rather than from another set of
 * flows, because that screen is already observing them.
 */
@Composable
fun CsvImportCard(
    accounts: List<Account>,
    categories: List<Category>,
    onImported: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CsvImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberCsvFilePicker(onPicked = viewModel::setFile)
    val account = accounts.firstOrNull { it.id == state.accountId }

    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.csv_title),
                style = MaterialTheme.typography.titleMedium,
            )

            FilePickerRow(
                state = state,
                accounts = accounts,
                onAccount = viewModel::setAccount,
                onPickFile = pickFile,
                onRead = { viewModel.read() },
            )

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            state.result?.let { result ->
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

            state.preview?.let { preview ->
                HorizontalDivider()
                MappingEditor(
                    preview = preview,
                    expanded = state.mappingExpanded,
                    onToggle = viewModel::toggleMapping,
                    onChange = viewModel::setMappingColumn,
                )
                PreviewWarnings(preview, account)
                PreviewRows(preview, state, account, accounts, categories, viewModel)
                Button(
                    onClick = { viewModel.confirm(onImported) },
                    enabled = !state.busy && state.freshCount > 0,
                ) {
                    Text(stringResource(Res.string.banks_import_count, state.freshCount))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilePickerRow(
    state: CsvImportUiState,
    accounts: List<Account>,
    onAccount: (Long?) -> Unit,
    onPickFile: () -> Unit,
    onRead: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PickerField(
            label = stringResource(Res.string.common_account),
            value = accounts.firstOrNull { it.id == state.accountId }?.name,
            options = accounts.filter { !it.archived }
                .map { PickerOption(it.id, "${it.name} (${it.currencyCode})") },
            onSelect = onAccount,
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
        )
        OutlinedButton(onClick = onPickFile, modifier = Modifier.widthIn(min = 200.dp)) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                // The chosen file's name once there is one - a file input that forgets
                // what it is holding is the classic way to import the wrong statement.
                state.fileName ?: stringResource(Res.string.csv_statement_file),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(onClick = onRead, enabled = state.canRead) {
            Icon(Icons.Filled.Upload, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(
                    if (state.busy) Res.string.csv_reading else Res.string.csv_read
                )
            )
        }
    }
}

/**
 * The detected column mapping, and a way to correct it.
 *
 * Shown rather than hidden because detection is a guess from the header row, and a file
 * with two date columns - Revolut ships "Started" and "Completed" - has no single right
 * answer. Collapsed by default, since the guess is usually right.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MappingEditor(
    preview: CsvPreview,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (CsvMappingField, String?) -> Unit,
) {
    val mapping = preview.mapping
    val summary = listOfNotNull(
        mapping.date,
        mapping.description,
        mapping.amount ?: listOfNotNull(mapping.debit, mapping.credit)
            .takeIf { it.isNotEmpty() }
            ?.joinToString("/"),
    ).joinToString(" · ")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onToggle) {
            Text("${stringResource(Res.string.csv_columns)}: $summary")
            Spacer(Modifier.size(6.dp))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }

        if (expanded) {
            val headers = preview.headers
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Date and description take no "none": the parser requires both, and
                // offering to clear them would only produce a file that will not read.
                HeaderPicker(
                    label = stringResource(Res.string.common_date),
                    value = mapping.date,
                    headers = headers,
                    allowNone = false,
                    onSelect = { onChange(CsvMappingField.DATE, it) },
                )
                HeaderPicker(
                    label = stringResource(Res.string.common_description),
                    value = mapping.description,
                    headers = headers,
                    allowNone = false,
                    onSelect = { onChange(CsvMappingField.DESCRIPTION, it) },
                )
                HeaderPicker(
                    label = stringResource(Res.string.common_amount),
                    value = mapping.amount,
                    headers = headers,
                    allowNone = true,
                    onSelect = { onChange(CsvMappingField.AMOUNT, it) },
                )
                HeaderPicker(
                    label = stringResource(Res.string.accounts_currency),
                    value = mapping.currency,
                    headers = headers,
                    allowNone = true,
                    onSelect = { onChange(CsvMappingField.CURRENCY, it) },
                )
            }
        }
    }
}

/**
 * Picks a column by its header.
 *
 * Keyed on the header's position rather than its text: [PickerField] selects by id, and
 * two columns in a CSV are perfectly entitled to share a name.
 */
@Composable
private fun HeaderPicker(
    label: String,
    value: String?,
    headers: List<String>,
    allowNone: Boolean,
    onSelect: (String?) -> Unit,
) {
    val noneLabel = stringResource(Res.string.common_none)
    val byPosition = headers.mapIndexed { index, header -> PickerOption(index.toLong(), header) }
    PickerField(
        label = label,
        value = value ?: noneLabel,
        options = if (allowNone) listOf(PickerOption(null, noneLabel)) + byPosition else byPosition,
        onSelect = { id -> onSelect(id?.let { headers[it.toInt()] }) },
        modifier = Modifier.widthIn(min = 160.dp, max = 220.dp),
    )
}

@Composable
private fun PreviewWarnings(preview: CsvPreview, account: Account?) {
    if (preview.currencyMismatch.isNotEmpty() && account != null) {
        Text(
            stringResource(
                Res.string.csv_currency_mismatch,
                preview.currencyMismatch.joinToString(", "),
                account.name,
                account.currencyCode,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    if (preview.skipped.isNotEmpty()) {
        Column {
            Text(
                pluralStringResource(
                    Res.plurals.csv_skipped_count,
                    preview.skipped.size,
                    preview.skipped.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The lines themselves, not just a count: "3 lines could not be read" is a
            // dead end, whereas the line usually says what is wrong with it.
            preview.skipped.take(SKIPPED_SHOWN).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Text(
        stringResource(
            Res.string.csv_summary,
            preview.fresh.size.toString(),
            (preview.rows.size - preview.fresh.size).toString(),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PreviewRows(
    preview: CsvPreview,
    state: CsvImportUiState,
    account: Account?,
    accounts: List<Account>,
    categories: List<Category>,
    viewModel: CsvImportViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        preview.rows.take(ROWS_SHOWN).forEach { row ->
            // The account's currency when the file names none: the server rescales into
            // the account's units on import, so that is what these amounts become.
            CsvRowLine(row, account?.currencyCode, state, accounts, categories, viewModel)
        }
    }

    if (preview.rows.size > ROWS_SHOWN) {
        Text(
            // Shown, not imported: the cap is on the list, and every row still goes.
            stringResource(Res.string.csv_truncated, preview.rows.size.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsvRowLine(
    row: CsvRow,
    fallbackCurrency: String?,
    state: CsvImportUiState,
    accounts: List<Account>,
    categories: List<Category>,
    viewModel: CsvImportViewModel,
) {
    val currency = row.currencyCode ?: fallbackCurrency
    val wanted =
        if (row.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val forType = categories.filter { !it.archived && it.type == wanted }
    // One id for both answers - a category, or the account a transfer moved to or from.
    val chosen = state.transfers[row.externalId]?.let { ImportChoice.encodeAccount(it) }
        ?: state.categories[row.externalId]

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

        if (currency != null) {
            MoneyText(
                money = Money(row.amountMinor, CurrencyCode(currency)),
                type = row.type,
                showSign = true,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (row.alreadyImported) {
            Text(
                stringResource(Res.string.csv_already_imported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PickerField(
                label = stringResource(Res.string.common_category),
                value = importChoiceLabel(chosen, row.type, forType, accounts),
                options = importChoiceOptions(row.type, forType, accounts, state.accountId),
                onSelect = { id -> viewModel.setRowChoice(row.externalId, id) },
                modifier = Modifier.widthIn(min = 180.dp, max = 240.dp),
            )
        }
    }
}

/** Enough to see what the file looks like without drawing thousands of rows. */
private const val ROWS_SHOWN = 200

private const val SKIPPED_SHOWN = 20
