package com.personalmoney.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable

/**
 * The parts of Settings that only some platforms have.
 *
 * The rest of the screen - currency, theme, theme, language - is the same
 * everywhere and lives in [SettingsScreen]. These three are not, and each is absent from
 * the web for a different and specific reason, so they are separate declarations rather
 * than one "platform extras" block: the reader should be able to see which capability is
 * missing, not just that something is.
 */

/** Version name and the privacy-policy URL, which the build supplies rather than the code. */
expect object AppInfo {
    val versionName: String
    val privacyPolicyUrl: String
}

/**
 * A function that opens a URL outside the app.
 *
 * Composable, because on Android opening one needs the Context that composition provides -
 * and a top-level function would have had to reach for it through a global.
 */
@Composable
expect fun rememberUrlOpener(): (String) -> Unit

/**
 * Export and import rows.
 *
 * Android only. The backup format is the Room schema - `BackupData` serialises the
 * entities directly - and the browser has no Room and no database to restore into. A web
 * export would have to mean something different (the server's own
 * `GET /api/users/me/export`), which is a product decision, not a port.
 */
@Composable
expect fun BackupSection(snackbar: SnackbarHostState)

/**
 * The biometric lock toggle.
 *
 * Android only, and deliberately not faked on the web: `navigator.credentials` could
 * prompt for a passkey, but that authenticates to a server rather than gating a local
 * screen, so it would look like the same feature while protecting nothing.
 */
@Composable
expect fun BiometricLockSection(viewModel: SettingsViewModel, enabled: Boolean)

/**
 * The in-app language picker.
 *
 * Android only, because only Android has an in-app language to pick: `AppCompatDelegate`
 * overrides the system locale for this app alone. A browser has no such notion - the page
 * follows the browser's own language setting, which is where a user changes it - so
 * offering a picker there would be offering a control that duplicates the browser's.
 */
@Composable
expect fun LanguageSection()

/**
 * The Material You toggle.
 *
 * Android only, because dynamic colour means one specific thing: a palette derived from the
 * user's wallpaper, which Android 12 exposes and a browser has no equivalent of. Its own
 * subtitle says "(Android 12+)" - offering it on the web would be offering a switch that
 * changes nothing.
 */
@Composable
expect fun DynamicColorSection(viewModel: SettingsViewModel, enabled: Boolean)
