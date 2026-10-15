package com.personalmoney.core.ui

import androidx.compose.runtime.Composable

/**
 * A function that hands a piece of text to whichever app the user picks - a messenger, mail,
 * a note - through the system's own share sheet.
 *
 * Composable for the same reason as `rememberUrlOpener`: on Android it needs the Context
 * that composition provides. Android only today; shared code asks for it by this name so
 * another platform can supply its own share mechanism.
 */
@Composable
expect fun rememberTextSharer(): (String) -> Unit
