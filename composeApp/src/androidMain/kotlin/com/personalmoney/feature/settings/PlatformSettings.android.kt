package com.personalmoney.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.BuildConfig
import com.personalmoney.core.security.BiometricGate
import com.personalmoney.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

actual object AppInfo {
    actual val versionName: String = BuildConfig.VERSION_NAME
    actual val privacyPolicyUrl: String = BuildConfig.PRIVACY_POLICY_URL
}

@Composable
actual fun rememberUrlOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            // runCatching: a device with no browser installed would otherwise crash the app
            // over a link to the privacy policy.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            Unit
        }
    }
}

@Composable
actual fun BackupSection(snackbar: SnackbarHostState) {
    val backup: BackupViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) backup.exportTo(uri) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) backup.requestImport(uri) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) backup.exportCsv(uri) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    }

    // Staged rather than immediate: an import replaces everything, so the user is told
    // what is in the file before it lands.
    val importRequest by backup.importRequest.collectAsStateWithLifecycle()
    importRequest?.let { req ->
        val p = req.preview
        AlertDialog(
            onDismissRequest = backup::cancelImport,
            title = { Text(stringResource(Res.string.settings_import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.settings_import_confirm_message,
                        p.accounts, p.categories, p.transactions, p.subscriptions
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    backup.confirmImport { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                }) { Text(stringResource(Res.string.settings_import_replace)) }
            },
            dismissButton = {
                TextButton(onClick = backup::cancelImport) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }

    SettingsItem(
        title = stringResource(Res.string.settings_export),
        onClick = { exportLauncher.launch("personalmoney-backup.json") }
    )
    SettingsItem(
        title = stringResource(Res.string.settings_import),
        onClick = { importLauncher.launch(arrayOf("application/json")) }
    )
    SettingsItem(
        title = stringResource(Res.string.settings_export_csv),
        onClick = { exportCsvLauncher.launch("personalmoney-transactions.csv") }
    )

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
}

@Composable
actual fun BiometricLockSection(viewModel: SettingsViewModel, enabled: Boolean) {
    val context = LocalContext.current
    val availability = remember { BiometricGate.availability(context) }

    // Resolved in composition: BiometricGate takes plain strings and the shared resource
    // reader is suspend, so it cannot be called from the click lambda.
    val lockTitle = stringResource(Res.string.lock_prompt_title)
    val lockSubtitle = stringResource(Res.string.lock_prompt_subtitle)
    val lockCancel = stringResource(Res.string.action_cancel)
    val subtitle = when (availability) {
        BiometricGate.Availability.AVAILABLE -> null
        BiometricGate.Availability.NOT_ENROLLED ->
            stringResource(Res.string.settings_biometric_lock_not_enrolled)
        else -> stringResource(Res.string.settings_biometric_lock_unavailable)
    }

    ToggleRow(
        title = stringResource(Res.string.settings_biometric_lock),
        subtitle = subtitle,
        checked = enabled,
        enabled = availability == BiometricGate.Availability.AVAILABLE,
        onCheckedChange = { wantsOn ->
            if (wantsOn) {
                // Turning the lock on has to pass the lock first, or the user could enable
                // something they cannot then get past.
                (context as? FragmentActivity)?.let { activity ->
                    BiometricGate.authenticate(
                        activity = activity,
                        title = lockTitle,
                        subtitle = lockSubtitle,
                        cancelLabel = lockCancel,
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
}

@Composable
actual fun DynamicColorSection(viewModel: SettingsViewModel, enabled: Boolean) {
    ToggleRow(
        title = stringResource(Res.string.settings_dynamic_color),
        subtitle = stringResource(Res.string.settings_dynamic_color_subtitle),
        checked = enabled,
        onCheckedChange = viewModel::setDynamicColor,
    )

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
}
