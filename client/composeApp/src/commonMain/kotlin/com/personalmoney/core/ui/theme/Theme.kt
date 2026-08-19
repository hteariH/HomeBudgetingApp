package com.personalmoney.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral20,
    outline = Indigo60
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo10,
    primaryContainer = Indigo20,
    onPrimaryContainer = Indigo90,
    secondary = Teal80,
    onSecondary = Teal10,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Amber10,
    tertiaryContainer = Amber40,
    onTertiaryContainer = Amber90,
    error = Red80,
    onError = Red10,
    errorContainer = Red40,
    onErrorContainer = Red90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    outline = Indigo60
)

/**
 * Material You, where the platform has it.
 *
 * Returns null when the platform cannot supply a scheme, which is every platform except
 * Android 12 and up - the caller then falls back to the palette above. Expressed as a
 * nullable return rather than a boolean capability check so the call site reads the same
 * everywhere.
 */
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

data class FinanceSemanticColors(
    val expense: Color,
    val income: Color,
    val transfer: Color
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceSemanticColors(expense = ExpenseRed, income = IncomeGreen, transfer = TransferBlue)
}

@Composable
fun PersonalMoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = (if (dynamicColor) platformDynamicColorScheme(darkTheme) else null)
        ?: if (darkTheme) DarkColors else LightColors
    val financeColors = FinanceSemanticColors(
        expense = ExpenseRed,
        income = IncomeGreen,
        transfer = TransferBlue
    )
    CompositionLocalProvider(LocalFinanceColors provides financeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PersonalMoneyTypography,
            content = content
        )
    }
}
