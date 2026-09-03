package com.personalmoney.core.ui

import androidx.compose.runtime.Composable

/**
 * Nothing to do: an Android app has no page to send anywhere.
 *
 * The one screen that navigates is not registered here at all - see
 * `navigation.homeMoneyImportAvailable` - so this exists to satisfy the expect
 * declaration and is never called.
 */
@Composable
actual fun rememberPageNavigator(): (String) -> Unit = {}
