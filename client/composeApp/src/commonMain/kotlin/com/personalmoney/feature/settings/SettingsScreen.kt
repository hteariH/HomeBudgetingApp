package com.personalmoney.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.personalmoney.core.ui.pageBarColors
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.ui.components.CurrencySelect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCurrencies: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlOpener()
    Scaffold(
        topBar = {
            TopAppBar(
                colors = pageBarColors(),
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel(stringResource(Res.string.settings_base_currency))
            // A search field rather than a row of chips: with every currency switched on
            // this is a hundred of them, and picking one out of that wall by eye is slower
            // than typing three letters.
            CurrencySelect(
                selected = state.baseCurrency,
                currencies = state.currencies.filter { it.active },
                onSelect = viewModel::setBaseCurrency,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            SettingsItem(
                title = stringResource(Res.string.settings_manage_currencies),
                subtitle = "${state.currencies.count { it.active }} / ${state.currencies.size}",
                onClick = onOpenCurrencies
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel(stringResource(Res.string.settings_theme))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                val themes = listOf(
                    ThemeMode.SYSTEM to Res.string.settings_theme_system,
                    ThemeMode.LIGHT to Res.string.settings_theme_light,
                    ThemeMode.DARK to Res.string.settings_theme_dark
                )
                items(themes) { (mode, label) ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(stringResource(label)) }
                    )
                }
            }

            DynamicColorSection(viewModel, state.dynamicColor)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Each of these draws its own trailing divider, and draws nothing at all on a
            // platform that lacks the capability - so the screen must not add separators
            // around them, or the web would show dividers with nothing between them.
            LanguageSection()

            BiometricLockSection(viewModel, state.biometricLockEnabled)

            BackupSection(snackbar)

            SettingsItem(
                title = stringResource(Res.string.settings_privacy_policy),
                onClick = { openUrl(AppInfo.privacyPolicyUrl) }
            )

            SettingsItem(
                title = stringResource(Res.string.settings_about),
                subtitle = stringResource(Res.string.settings_version) + " " + AppInfo.versionName,
                onClick = { }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            DeleteAccountSection(
                state = deletion,
                onAsk = viewModel::confirmDeleteAccount,
                onDismiss = viewModel::dismissDeleteAccount,
                onConfirm = viewModel::deleteAccount,
            )
        }
    }
}

/**
 * The way out of the product, at the bottom of the last screen where it belongs.
 *
 * Play requires an in-app route to deleting an account for any app that lets people
 * create one, so this is not optional dressing. It is deliberately awkward to trigger by
 * accident - error colour, a dialog that spells out what goes - and deliberately not
 * hidden behind a support email, which is the pattern Play rejects.
 */
@Composable
private fun DeleteAccountSection(
    state: AccountDeletionState,
    onAsk: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    SectionLabel(stringResource(Res.string.settings_danger_zone))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.inProgress, onClick = onAsk)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_delete_account),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(Res.string.settings_delete_account_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (!state.confirming) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_delete_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.settings_delete_account_body))
                if (state.error != null) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.inProgress) {
                Text(
                    text = stringResource(
                        if (state.inProgress) Res.string.settings_delete_account_working
                        else Res.string.settings_delete_account_confirm
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.inProgress) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
    )
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Read-only switch — the whole row is the single toggle target (avoids a
        // duplicate accessibility focus and accidental double toggles).
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@Composable
internal fun SettingsItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
