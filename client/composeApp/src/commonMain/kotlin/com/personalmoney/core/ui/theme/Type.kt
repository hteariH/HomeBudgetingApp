package com.personalmoney.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.personalmoney.resources.Res
import com.personalmoney.resources.inter_variable
import org.jetbrains.compose.resources.Font

/**
 * The web app's type scale, in the web app's typeface.
 *
 * Inter is bundled rather than asked of the platform, and it is not only about matching the
 * look. The font the wasm build fell back to has no ₴ - hryvnia rendered as an empty box on
 * every Monobank account, while € beside it was fine. A finance app that cannot draw its
 * users' currency is broken, and no test noticed: it is only visible in a browser. Inter
 * covers ₴ ₽ ₸ €, Cyrillic including ї and ґ, and U+2212, the true minus sign `MoneyText`
 * uses for expenses.
 *
 * One variable file rather than five static weights: the app uses 400 through 800, which
 * would otherwise be five downloads and roughly twice the bytes.
 *
 * Two things follow the web app's CSS that Material's default does not: body text is 14sp
 * rather than 16sp, and headings are heavy - `font-extrabold` on the auth titles. Letter
 * spacing is zero throughout; the CSS never adds any, and Material's default tracking reads
 * as a different typeface at a glance.
 */
@Composable
fun personalMoneyTypography(): Typography {
    val inter = interFamily()
    return remember(inter) { typographyOf(inter) }
}

/**
 * The weights the scale actually uses, each an instance of the same variable file.
 *
 * Registering the axis explicitly matters: without it the renderer takes the font's default
 * instance and fakes the heavier weights by smearing the outline, which is exactly what
 * "bold" looks like when it is wrong.
 */
@Composable
private fun interFamily(): FontFamily = FontFamily(
    interWeight(FontWeight.Normal, 400),
    interWeight(FontWeight.Medium, 500),
    interWeight(FontWeight.SemiBold, 600),
    interWeight(FontWeight.Bold, 700),
    interWeight(FontWeight.ExtraBold, 800),
)

@Composable
private fun interWeight(weight: FontWeight, axis: Int) = Font(
    Res.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

private fun typographyOf(f: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp),
    displaySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.5).sp),

    // The auth titles: `text-2xl sm:text-3xl font-extrabold tracking-tight`.
    headlineLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),

    // `.section-title` and the card headings.
    titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),

    // 14px body with the CSS's 1.6 line-height; 15px where the web app steps up.
    bodyLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),

    // `.form-label` is 13px/500; `.btn` is 14px/500.
    labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
