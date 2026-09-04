package com.personalmoney.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personalmoney.core.data.ThemeMode

/**
 * What [ThemeMode] means in pixels, in one place.
 *
 * Three callers need this answer and would otherwise each spell out the same `when`: the
 * Android activity and the web entry point, which pick the colour scheme, and the header's
 * theme switch, which has to know what it is switching *away from* - a toggle offering
 * "switch to dark" while the window is already dark is worse than no toggle.
 */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * The web app's four surface layers, mapped onto Material's slots.
 *
 * Material names its containers by elevation; the web app names them by what sits in them.
 * They line up: `background` is the page, `surface` is the chrome around it,
 * `surfaceContainer` is a card, `surfaceContainerHigh` is a card being hovered. Keeping
 * that mapping explicit here means the screens can keep using ordinary Material slots and
 * still come out looking like the web app.
 */
private val DarkColors = darkColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = AccentDark,
    onPrimaryContainer = Color.White,
    // The accent again rather than a second hue: the web app has one accent, and inventing
    // a secondary here would show up as a colour that exists nowhere in the design.
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = DarkHover,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = StatusCyan,
    onTertiary = Color.White,
    tertiaryContainer = DarkHover,
    onTertiaryContainer = DarkTextPrimary,
    error = StatusRed,
    onError = Color.White,
    errorContainer = Color(0xFF3A1D1D),
    onErrorContainer = Color(0xFFFCA5A5),
    background = DarkBase,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkHover,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkBase,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkHover,
    surfaceContainerHighest = DarkHover,
    outline = DarkBorderLight,
    outlineVariant = DarkBorder,
)

/**
 * The same mapping in light, with the accent one step down.
 *
 * `Accent` is the indigo the dark theme uses, and it is the right one there: on a near-black
 * card it is the brightest thing on screen. On white it is 4.47:1, which is under AA and
 * reads as a pastel - a filled button in white text that looks disabled next to the same
 * button in dark mode. `AccentDark` is 6.29:1 and is the same indigo the web app's light
 * mode uses, so the two clients still show one accent.
 */
private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    // Material would put a pale indigo tint here, and that is what this used to be: #E0E7FF,
    // which against the page is 1.04:1. Everything built on this slot - the balance hero, the
    // FAB, a selected chip - is the loudest thing on its screen in dark and vanished in
    // light. It is the same fill as dark now, so a hero is a hero in both.
    primaryContainer = AccentDark,
    onPrimaryContainer = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = LightHover,
    onSecondaryContainer = LightTextPrimary,
    tertiary = Color(0xFF0E7490),
    onTertiary = Color.White,
    tertiaryContainer = LightHover,
    onTertiaryContainer = LightTextPrimary,
    error = ExpenseRedLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    background = LightBase,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightHover,
    onSurfaceVariant = LightTextSecondary,
    // Light's ladder runs the other way - a container is *whiter* than the page it sits on,
    // so the "low" end is white and the steps go down into the tinted greys, mirroring dark
    // rather than copying its ordering.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightCard,
    surfaceContainerHigh = LightHover,
    surfaceContainerHighest = LightBorder,
    outline = LightBorderLight,
    outlineVariant = LightBorder,
)

/**
 * The web app's radii: 6, 10, 16, 24.
 *
 * Material's default scale is rounder (up to 28dp) and reads as a different product.
 * Cards, sheets, dialogs and text fields pick these up on their own.
 *
 * Buttons do not: Material 3 pins them to a pill regardless of this scale, so a button
 * that should look like the web app's has to say `shape = MaterialTheme.shapes.small` for
 * itself. [PmButtonShape] is that value under a name, so the reason is written down once.
 */
val PersonalMoneyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** The web app's `--radius-sm`, which is what every button and input there uses. */
val PmButtonShape = RoundedCornerShape(6.dp)

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

/**
 * The colours Material has no slot for.
 *
 * [muted] is the web app's third text step, used for placeholders and the quiet half of a
 * two-line row. [border] is its `--border`: Material's `outlineVariant` carries the same
 * value, but a border is drawn often enough here to deserve a name that says so.
 */
data class FinanceSemanticColors(
    val expense: Color,
    val income: Color,
    val transfer: Color,
    val muted: Color,
    val border: Color,
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceSemanticColors(
        expense = ExpenseRedDark,
        income = IncomeGreenDark,
        transfer = TransferBlueDark,
        muted = DarkTextMuted,
        border = DarkBorder,
    )
}

@Composable
fun PersonalMoneyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Off by default now. Material You repaints the whole app from the phone's wallpaper,
     * which is the opposite of what this palette is for - the point is that the phone and
     * the browser show one product. The Android setting still exists for anyone who
     * prefers their wallpaper's colours; it simply no longer wins by default.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = (if (dynamicColor) platformDynamicColorScheme(darkTheme) else null)
        ?: if (darkTheme) DarkColors else LightColors

    // Per theme, not fixed: the money colours have to stay readable as text on both a
    // near-black card and a white one. See Color.kt for the contrast measurements.
    val financeColors = if (darkTheme) {
        FinanceSemanticColors(
            expense = ExpenseRedDark,
            income = IncomeGreenDark,
            transfer = TransferBlueDark,
            muted = DarkTextMuted,
            border = DarkBorder,
        )
    } else {
        FinanceSemanticColors(
            expense = ExpenseRedLight,
            income = IncomeGreenLight,
            transfer = TransferBlueLight,
            muted = LightTextMuted,
            border = LightBorder,
        )
    }

    CompositionLocalProvider(LocalFinanceColors provides financeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = personalMoneyTypography(),
            shapes = PersonalMoneyShapes,
            content = content
        )
    }
}
