package com.personalmoney.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Browsers expose no system accent colour, so the app palette always wins here. */
@Composable
actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
