package com.personalmoney.core.ui

import androidx.compose.runtime.Composable

/**
 * Sends the whole page somewhere else, rather than opening a tab.
 *
 * Deliberately not `rememberUrlOpener`, which opens a new tab with `noopener`. That is the
 * right behaviour for a privacy policy and the wrong one for an OAuth round trip: consent
 * ends in a redirect back to this app, and in a new tab that redirect lands in the tab the
 * user is not looking at, leaving the original one none the wiser.
 *
 * The only caller is the HomeMoney import, which is web-only - see
 * `navigation.homeMoneyImportAvailable`.
 */
@Composable
expect fun rememberPageNavigator(): (String) -> Unit
