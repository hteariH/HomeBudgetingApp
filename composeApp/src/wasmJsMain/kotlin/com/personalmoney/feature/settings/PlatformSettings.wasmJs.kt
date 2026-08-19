package com.personalmoney.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

@JsFun("(u) => window.open(u, '_blank', 'noopener')")
private external fun windowOpen(url: String)

/**
 * The web build has no Gradle-generated BuildConfig. These are the same values
 * `composeApp/build.gradle.kts` puts in the Android one; when the web bundle gains its own
 * versioning in phase 9, this is where it reads it from.
 */
actual object AppInfo {
    actual val versionName: String = "0.1.0"
    actual val privacyPolicyUrl: String =
        "https://gist.github.com/hteariH/bfd7d6412cc41a227bcc53646655bfc2"
}

/** `noopener` so the opened page cannot reach back through `window.opener`. */
@Composable
actual fun rememberUrlOpener(): (String) -> Unit = ::windowOpen

/** Nothing to draw - see the shared declaration for why the web has no backup rows. */
@Composable
actual fun BackupSection(snackbar: SnackbarHostState) = Unit

/** Nothing to draw - see the shared declaration for why the web has no biometric lock. */
@Composable
actual fun BiometricLockSection(viewModel: SettingsViewModel, enabled: Boolean) = Unit
