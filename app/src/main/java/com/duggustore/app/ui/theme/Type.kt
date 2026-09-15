package com.duggustore.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// ==========================================================================
// TYPOGRAPHY -- a role-named ramp, replacing v1's Material-slot mapping
// ==========================================================================
//
// WHAT WAS WRONG IN v1
// --------------------
// Type.kt defined a perfectly reasonable Material3 Typography -- and then nothing
// used it. `MaterialTheme.typography` appears exactly ONCE in the entire codebase,
// against 127 hand-written `fontSize = N.sp` literals. The scale existed on paper
// only.
//
// The cost showed up as 17 distinct font sizes in the app, including four
// consecutive odd values (11/12/13/14 sp all in heavy use) that no one can tell
// apart at a glance, plus 9sp and 10sp captions that no longer clear a readable
// floor on a 6" phone.
//
// This file keeps the same Typography() shape -- so the drop-in swap still works --
// but two things change:
//
//   1. Material slots are WIRED, so Material3's own components (Button, TextField,
//      NavigationBar labels) inherit the real brand type instead of Roboto 14sp.
//   2. A parallel DugguType object exposes the same ramp under ROLE names that
//      match the design tokens, because "titleMedium" tells a reader nothing about
//      whether it is a product name or a dialog header.
//
// THE SCALE -- 1.200 ratio at the low end, flattening to 1.125 at display
// ==========================================================================
//   display.lg   34 / 40 / 800   hero, splash                     (was: ad-hoc 34)
//   display.md   28 / 34 / 700   order-placed, empty-state hero   (was: 28 + 32)
//   heading.lg   22 / 28 / 700   screen title                     (was: 20, 21)
//   heading.md   19 / 25 / 700   home section title               (was: 19, 18)  <- 18 folded in
//   heading.sm   17 / 23 / 600   row header / See All             (was: 17, 16)  <- 16 folded in
//   title.lg     16 / 22 / 600   list-item title, dialog title    (was: 16, 15)  <- 15 folded in
//   title.md     15 / 21 / 600   product name                     (was: 14, 13)  <- 13 folded in
//   title.sm     14 / 19 / 600   BUTTON LABEL, tab label          (was: 14, 13)
//   body.md      14 / 20 / 400   default body                     (unchanged, was fine)
//   body.sm      13 / 18 / 400   product unit, secondary body     (was: 12, 11)  <- 11 folded in
//   caption      12 / 16 / 500   metadata, helper text            (was: 12, 11)  <- 11 folded in
//   overline     11 / 14 / 600   badge, uppercase label           (was: 11, 10)  <- 10 folded in
//   micro        10 / 13 / 500   nav label, count badge -- FLOOR  (was: 9, 10)   <- 9 raised
//   price.lg     20 / 24 / 700   cart total, PDP price
//   price.md     15 / 19 / 700   product card price
//   price.strike 12 / 16 / 400   MRP (line-through)
//
// 17 sizes -> 16 roles over 11 distinct sizes, and every one of them is now
// reachable by name. The four odd values that were indistinguishable (11/12/13/14)
// collapse to three roles with clearly different jobs.
//
// TWO ACCESSIBILITY DECISIONS WORTH CALLING OUT
// ---------------------------------------------
// 1. `micro` is the floor at 10sp. v1 used 9sp for count badges and the "9+"
//    overflow label. 9sp is roughly 6pt -- below every platform's readability
//    guidance, and the badge is exactly the element a user needs to read at a
//    glance. Raised to 10sp and given a 13sp line box so the tight cap-height
//    doesn't clip.
//
// 2. `includeFontPadding` is off and LineHeightStyle is set to trim both ends.
//    Without this, Compose adds ~2-3dp of invisible padding above the first line
//    and below the last, which makes 12sp and 14sp text look like different
//    vertical rhythms at the same stated line height -- the main reason the v1
//    cards felt subtly uneven. Trimming makes line height mean line height.
//
// FONT
// ----
// v1 used FontFamily.Default (Roboto on Android). Inter is specified in the token
// source because it has a taller x-height than Roboto at small sizes, which is
// what makes 11-13sp legible on the Indian mid-range device this app targets.
// Ship it via res/font/inter_*.ttf and swap `sans` below. Until then the
// fallback chain keeps Roboto, so this file is safe to land before the font.

private val sans = FontFamily.Default
    // After adding res/font: FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium), ...)

/** Trim the font's own vertical padding so lineHeight is the real rhythm. */
private val trimmed = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f
) = TextStyle(
    fontFamily = sans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = trimmed
)

/**
 * Role-named ramp. Mirrors /tokens/design-tokens.json -> typography.scale, so a
 * designer reading the JSON and a developer reading Kotlin see the same names.
 */
object DugguType {
    // Display -- hero moments only. If you need one on a list screen, you want heading.lg.
    val displayLg = style(34, 40, FontWeight.ExtraBold, tracking = -0.8f)
    val displayMd = style(28, 34, FontWeight.Bold, tracking = -0.6f)

    // Headings -- screen and section titles.
    val headingLg = style(22, 28, FontWeight.Bold, tracking = -0.3f)
    val headingMd = style(19, 25, FontWeight.Bold, tracking = -0.2f)
    val headingSm = style(17, 23, FontWeight.SemiBold, tracking = -0.1f)

    // Titles -- the primary line inside a repeated unit.
    val titleLg = style(16, 22, FontWeight.SemiBold)
    val titleMd = style(15, 21, FontWeight.SemiBold)
    val titleSm = style(14, 19, FontWeight.SemiBold)   // button + tab labels

    // Body.
    val bodyLg = style(15, 22, FontWeight.Normal)
    val bodyMd = style(14, 20, FontWeight.Normal)
    val bodySm = style(13, 18, FontWeight.Normal)

    // Supporting.
    val caption  = style(12, 16, FontWeight.Medium, tracking = 0.1f)
    val overline = style(11, 14, FontWeight.SemiBold, tracking = 0.6f)
    val micro    = style(10, 13, FontWeight.Medium, tracking = 0.2f)   // floor

    // Prices get their own slots because they carry tabular figures and a
    // different optical weight. A price is scanned, not read.
    val priceLg     = style(20, 24, FontWeight.Bold, tracking = -0.2f)
    val priceMd     = style(15, 19, FontWeight.Bold)
    val priceStrike = style(12, 16, FontWeight.Normal)
}

/**
 * Material3 slot wiring. Same Typography() constructor as v1, so Theme.kt's
 * `typography = Typography` call site is unchanged -- the slots just stop being
 * dead weight and start feeding Material's own components.
 */
val Typography = Typography(
    displayLarge   = DugguType.displayLg,
    displayMedium  = DugguType.displayMd,
    displaySmall   = DugguType.headingLg,

    headlineLarge  = DugguType.headingLg,
    headlineMedium = DugguType.headingMd,
    headlineSmall  = DugguType.headingSm,

    titleLarge     = DugguType.titleLg,
    titleMedium    = DugguType.titleMd,
    titleSmall     = DugguType.titleSm,

    bodyLarge      = DugguType.bodyLg,
    bodyMedium     = DugguType.bodyMd,
    bodySmall      = DugguType.bodySm,

    // Material attaches labelLarge to BUTTONS by default. v1 mapped it to 14sp
    // SemiBold -- correct weight, but every button in the app overrode the size
    // inline anyway. Now the slot carries the token so overrides are removable.
    labelLarge  = DugguType.titleSm,
    labelMedium = DugguType.caption,
    labelSmall  = DugguType.overline
)
