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

// Light stacks the other way. On a dark page a raised layer is lighter; on a light page it
// can only be whiter, and white is the ceiling - so the light ladder is built *downwards*
// from the card: the page is a tinted step below white and hover a step below that. The
// first version of it kept the page at #F8FAFC under white cards, 1.05:1 apart, which is a
// difference no screen actually draws - every card, sheet and dialog dissolved into the
// page and the whole theme read as one flat sheet of white. These values put the page
// 1.20:1 below a card, the same separation the dark theme has (1.22:1).
val LightBase = Color(0xFFE6EBF3)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFFFFFFF)
val LightHover = Color(0xFFD4DDE9)
// Borders carry more here than in dark, because they are the only hard edge a white card
// has. #E2E8F0 on white is 1.23:1 - present in a mockup, gone on a laptop screen at an
// angle. These are 1.62:1 and 2.56:1.
val LightBorder = Color(0xFFC3CCDA)
val LightBorderLight = Color(0xFF94A3B8)

// ── Text ─────────────────────────────────────────────────────────────────────
// Three steps, and the third has no Material slot - see FinanceSemanticColors.muted.
val DarkTextPrimary = Color(0xFFF0F2F8)
val DarkTextSecondary = Color(0xFF8B93B0)
val DarkTextMuted = Color(0xFF555D7C)

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
// A step darker than the web app's old #64748B, which was 4.76:1 on white and dropped under
// AA the moment the same text landed on a hover row or a tinted panel. This clears 4.5:1 on
// every light surface in the palette.
val LightTextMuted = Color(0xFF5A6779)

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
 * The dark pair is the web app's `--green` and `--red` exactly. The light pair is darker on
 * the same hues, and that is a deliberate departure: as *text* on a white card, `#10B981`
 * sits at 2.47:1 contrast, which is not readable. Two steps down rather than one, because
 * one was only enough against pure white - `#059669` is 3.77:1 on a card but 3.15:1 on the
 * page, and an amount is body text, not a chart fill. These clear AA on both: income 5.48:1
 * on a card and 4.58:1 on the page, expense 5.74:1 and 4.79:1.
 *
 * Darkening does not cost colour-blind separation - the new light pair separates slightly
 * better under deuteranopia than the old one did, and both pairs stay well clear of the
 * floor the previous Material-derived palette missed, where red and green sat close enough
 * that position and a legend had to carry the meaning.
 */
val IncomeGreenDark = Color(0xFF10B981)
val ExpenseRedDark = Color(0xFFEF4444)
val IncomeGreenLight = Color(0xFF047857)
val ExpenseRedLight = Color(0xFFC81E1E)

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
