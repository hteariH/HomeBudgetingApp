package com.personalmoney.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette, taken from the React client's design tokens
 * (`src/main/frontend/src/index.css`).
 *
 * Not a Material palette generated from a seed colour. The web app has a deliberate look -
 * near-black layered surfaces, an indigo accent, a slate-blue text ramp - and the point of
 * one codebase is that the phone and the browser show the same product, not two products
 * that happen to share a server. Every value below is the same hex the web app ships.
 *
 * The names say what the colour is *for*, which is how the CSS names them too. A colour
 * named after its role survives a redesign; one named after its hue does not.
 */

// ── Surfaces ─────────────────────────────────────────────────────────────────
// Four layers, not two. The depth in the web app comes from stacking base → surface →
// card → hover, each a step lighter, with a 1px border between them.
val DarkBase = Color(0xFF0D0F14)
val DarkSurface = Color(0xFF161A23)
val DarkCard = Color(0xFF1E2330)
val DarkHover = Color(0xFF252B3B)
val DarkBorder = Color(0xFF2D3448)
val DarkBorderLight = Color(0xFF3A4260)

val LightBase = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFFFFFFF)
val LightHover = Color(0xFFF1F5F9)
val LightBorder = Color(0xFFE2E8F0)
val LightBorderLight = Color(0xFFCBD5E1)

// ── Text ─────────────────────────────────────────────────────────────────────
// Three steps, and the third has no Material slot - see FinanceSemanticColors.muted.
val DarkTextPrimary = Color(0xFFF0F2F8)
val DarkTextSecondary = Color(0xFF8B93B0)
val DarkTextMuted = Color(0xFF555D7C)

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF64748B)

// ── Accent ───────────────────────────────────────────────────────────────────
// Indigo 500/400/600. The light step carries the accent on dark surfaces, where the
// base indigo is too close to the background to read as interactive.
val Accent = Color(0xFF6366F1)
val AccentLight = Color(0xFF818CF8)
val AccentDark = Color(0xFF4F46E5)

// ── Status ───────────────────────────────────────────────────────────────────
val StatusRed = Color(0xFFEF4444)
val StatusYellow = Color(0xFFF59E0B)
val StatusCyan = Color(0xFF06B6D4)
val StatusPurple = Color(0xFFA855F7)

// ── Money ────────────────────────────────────────────────────────────────────
/**
 * Income and expense, per theme.
 *
 * The dark pair is the web app's `--green` and `--red` exactly. The light pair is one
 * step darker on the same hues, and that is a deliberate departure: as *text* on a white
 * card, `#10B981` sits at 2.47:1 contrast, which is not readable. The darker steps clear
 * 3:1 and improve colour-blind separation as well (ΔE 8.6 against 8.1 under deuteranopia),
 * so nothing is lost by it.
 *
 * Both pairs clear the ΔE 8 floor for deuteranopia, which the previous Material-derived
 * palette did not - it sat at 4.9 and needed position and a legend to carry the meaning.
 */
val IncomeGreenDark = Color(0xFF10B981)
val ExpenseRedDark = Color(0xFFEF4444)
val IncomeGreenLight = Color(0xFF059669)
val ExpenseRedLight = Color(0xFFDC2626)

/**
 * Transfers: neither income nor expense, so a third hue - and a measured one.
 *
 * The obvious pick was the palette's cyan, and it failed: against the income green it
 * scores ΔE 12.5 for a viewer with full colour vision, under the 15 floor, and 3.4 under
 * tritanopia. Teal beside green is a distinction most people cannot make.
 *
 * A plain blue separates from green cleanly but lands ΔE 7.2 from the indigo accent, which
 * would make every transfer look like a link. These two sit between: 18+ from the income
 * green and 15+ from the accent in both themes, with the light step darkened for contrast
 * on white the same way the money colours are.
 */
val TransferBlueDark = Color(0xFF0EA5E9)
val TransferBlueLight = Color(0xFF0369A1)
