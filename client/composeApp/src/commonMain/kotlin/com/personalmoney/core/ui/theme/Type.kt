package com.personalmoney.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The web app's type scale.
 *
 * Two things there that Material's default does not do, and both are visible immediately:
 * the body size is 14sp rather than 16sp, and headings are heavy - `font-extrabold` on the
 * auth titles, 600 on section headings - where Material tops out at SemiBold for display
 * styles. Letter spacing is zero throughout; the CSS never adds any, and Material's
 * default tracking on body text is enough to read as a different typeface at a glance.
 *
 * The family is still the platform default rather than the web app's Inter. Shipping Inter
 * would mean the font files in the wasm bundle, which is already the number this migration
 * is judged on - worth doing deliberately, not as a side effect of a palette change.
 */
private val Default = FontFamily.Default

val PersonalMoneyTypography = Typography(
    displayLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),

    // The auth titles: `text-2xl sm:text-3xl font-extrabold tracking-tight`.
    headlineLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),

    // `.section-title` and the card headings.
    titleLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),

    // 14px body with the CSS's 1.6 line-height; 15px where the web app steps up.
    bodyLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),

    // `.form-label` is 13px/500; `.btn` is 14px/500.
    labelLarge = TextStyle(fontFamily = Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Default, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
