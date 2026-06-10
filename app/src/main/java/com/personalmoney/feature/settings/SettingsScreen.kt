package com.personalmoney.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.BuildConfig
import com.personalmoney.R
import com.personalmoney.core.data.ThemeMode
import com.personalmoney.core.locale.LocaleManager
import com.personalmoney.core.security.BiometricGate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCurrencies: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val biometricAvailability = remember { BiometricGate.availability(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportTo(uri) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFrom(uri) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            SectionLabel(stringResource(R.string.settings_base_currency))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
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
                title = stringResource(R.string.settings_manage_currencies),
                subtitle = "${state.currencies.count { it.active }} / ${state.currencies.size}",
                onClick = onOpenCurrencies
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionLabel(stringResource(R.string.settings_theme))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                val themes = listOf(
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark
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
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                checked = state.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Numpad layout
            SectionLabel(stringResource(R.string.settings_numpad_layout))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = !state.numpadPhoneLayout,
                        onClick = { viewModel.setNumpadPhoneLayout(false) },
                        label = { Text(stringResource(R.string.settings_numpad_calc)) }
                    )
                }
                item {
                    FilterChip(
                        selected = state.numpadPhoneLayout,
                        onClick = { viewModel.setNumpadPhoneLayout(true) },
                        label = { Text(stringResource(R.string.settings_numpad_phone)) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Language picker
            SectionLabel(stringResource(R.string.settings_language))
            var currentLangTag by remember { mutableStateOf(LocaleManager.currentTag()) }
            val availableLangs = remember(currentLangTag) { LocaleManager.availableLanguages() }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    FilterChip(
                        selected = currentLangTag == null,
                        onClick = {
                            LocaleManager.apply(null)
                            currentLangTag = null
                        },
                        label = { Text(stringResource(R.string.settings_language_system)) }
                    )
                }
                items(availableLangs) { lang ->
                    FilterChip(
                        selected = currentLangTag.equals(lang.tag, ignoreCase = true),
                        onClick = {
                            LocaleManager.apply(lang.tag)
                            currentLangTag = lang.tag
                        },
                        label = { Text(stringResource(lang.labelRes)) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Biometric lock
            val biometricEnabled = state.biometricLockEnabled
            val biometricSubtitle = when (biometricAvailability) {
                BiometricGate.Availability.AVAILABLE -> null
                BiometricGate.Availability.NOT_ENROLLED -> stringResource(R.string.settings_biometric_lock_not_enrolled)
                else -> stringResource(R.string.settings_biometric_lock_unavailable)
            }
            ToggleRow(
                title = stringResource(R.string.settings_biometric_lock),
                subtitle = biometricSubtitle,
                checked = biometricEnabled,
                enabled = biometricAvailability == BiometricGate.Availability.AVAILABLE,
                onCheckedChange = { wantsOn ->
                    if (wantsOn) {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            BiometricGate.authenticate(
                                activity = activity,
                                title = context.getString(R.string.lock_prompt_title),
                                subtitle = context.getString(R.string.lock_prompt_subtitle),
                                cancelLabel = context.getString(R.string.action_cancel),
                                onSuccess = { viewModel.setBiometricLockEnabled(true) },
                                onFailure = { _, _ -> }
                            )
                        }
                    } else {
                        viewModel.setBiometricLockEnabled(false)
                    }
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SettingsItem(
                title = stringResource(R.string.settings_export),
                onClick = { exportLauncher.launch("personalmoney-backup.json") }
            )
            SettingsItem(
                title = stringResource(R.string.settings_import),
                onClick = { importLauncher.launch(arrayOf("application/json")) }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SettingsItem(
                title = stringResource(R.string.settings_privacy_policy),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL))
                    runCatching { context.startActivity(intent) }
                }
            )

            SettingsItem(
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version) + " 0.1.0",
                onClick = { }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun SettingsItem(
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
