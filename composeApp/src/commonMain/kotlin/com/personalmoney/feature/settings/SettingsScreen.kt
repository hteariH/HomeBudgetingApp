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
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.data.ThemeMode
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlOpener()
    Scaffold(
        topBar = {
            TopAppBar(
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
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(state.currencies.filter { it.active }) { currency ->
                    FilterChip(
                        selected = currency.code == state.baseCurrency,
                        onClick = { viewModel.setBaseCurrency(currency.code) },
                        label = { Text(currency.code) }
                    )
                }
            }

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

            ToggleRow(
                title = stringResource(Res.string.settings_dynamic_color),
                subtitle = stringResource(Res.string.settings_dynamic_color_subtitle),
                checked = state.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Numpad layout
            SectionLabel(stringResource(Res.string.settings_numpad_layout))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = !state.numpadPhoneLayout,
                        onClick = { viewModel.setNumpadPhoneLayout(false) },
                        label = { Text(stringResource(Res.string.settings_numpad_calc)) }
                    )
                }
                item {
                    FilterChip(
                        selected = state.numpadPhoneLayout,
                        onClick = { viewModel.setNumpadPhoneLayout(true) },
                        label = { Text(stringResource(Res.string.settings_numpad_phone)) }
                    )
                }
            }

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
        }
    }
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
