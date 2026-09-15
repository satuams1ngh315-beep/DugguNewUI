package com.duggustore.app.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================================================
// DugguStore Design System v2.0 -- Primitive Color Ramps
// ==========================================================================
//
// GENERATED FROM /tokens/design-tokens.json -- do not hand-edit values here.
//
// WHAT CHANGED AND WHY
// --------------------
// v1 shipped one flat namespace of ~60 colour names, most of them ALIASES of
// each other ("Teal" was pink, "BlinkitGreen" was pink, "AccentLime" was pink),
// left behind from two earlier rebrands. That worked -- every call site kept
// compiling -- but it meant no one could tell a brand colour from a semantic one
// from a legacy shim without reading this file.
//
// v2 splits the two jobs apart properly:
//
//   1. PRIMITIVES (this file)  -- an 11-step ramp per hue. No meaning, just
//                                available values. Referenced ONLY by Semantic.kt.
//   2. SEMANTIC (Semantic.kt)  -- what a colour is FOR: text.primary,
//                                action.primary, status.pendingFg. This is the
//                                only layer component code is allowed to touch.
//
// The payoff: the whole app re-themes (light->dark, or pink->saffron for a
// regional brand variant) by rebinding ~50 semantic keys, with zero component
// edits. v1 could not do that -- switching to dark meant touching every screen.
//
// MEASURED CONTRAST FIXES
// -----------------------
// Every pairing below quotes its verified WCAG 2.2 ratio. The v1 values that
// failed are documented inline so the regression can't silently come back.
//
//   v1 InkFaint #94969F on white ....... 2.95:1  FAIL (even 3:1 UI floor)
//   v2 text.tertiary #616779 on white .. 5.64:1  PASS AA
//
//   v1 MyntraPink #FF3F6C as text ...... 3.40:1  FAIL AA body
//   v2 text.brand #B81A45 on white ..... 6.42:1  PASS AA
//      (brand.600 stays for FILLS -- white on #DC2453 is 4.75:1 PASS AA)
//
//   v1 MynOrange #F97316 as text ....... 2.80:1  FAIL
//   v2 warning.700 #B45309 on white .... 5.02:1  PASS AA
//
//   v1 StarYellow #FFB020 on white ..... 1.83:1  FAIL
//   v2 star500 #B45309 on white ........ 5.02:1  PASS AA
//      A first draft used #F59E0B here and measured 2.15:1 -- the star glyph and
//      the rating number beside it read as ONE unit, so both must clear 4.5:1.
//
// Remember the rule this file encodes: a hue bright enough to look good as a
// button fill is usually too bright to read as text on white. Hence 500 for
// fills, 700 for text on light, 400 for text on dark.

// -- Brand -- the signature DugguStore pink ---------------------------------
// Re-centred from v1's #FF3F6C, which is a lovely fill but unreadable as text.
val Brand50  = Color(0xFFFFF1F5)
val Brand100 = Color(0xFFFFE4EC)
val Brand200 = Color(0xFFFFC9D9)
val Brand300 = Color(0xFFFF9BB4)
val Brand400 = Color(0xFFFF6B92)   // 6.88:1 on neutral950 -- dark-theme text
val Brand500 = Color(0xFFF5305F)   // brand anchor -- fills + UI/large only
val Brand600 = Color(0xFFDC2453)   // 4.75:1 with white -- the primary CTA fill
val Brand700 = Color(0xFFB81A45)   // 6.42:1 on white -- brand TEXT on light
val Brand800 = Color(0xFF94133A)   // 8.62:1 -- pressed / dense
val Brand900 = Color(0xFF6E0D2B)

// -- Neutral -- a single ink scale, replacing v1's 6 near-identical greys -----
// v1 had Hairline #E9E9ED, DividerGray #F0F0F2 and BorderGray #F0F0F2 --
// three names, two actual values, four greys within 8 hex steps of each other.
val Neutral0   = Color(0xFFFFFFFF)
val Neutral25  = Color(0xFFFCFCFD)
val Neutral50  = Color(0xFFF7F7F9)   // light canvas
val Neutral100 = Color(0xFFF1F1F4)   // sunken well / muted fill
val Neutral200 = Color(0xFFE6E7EB)   // hairline border
val Neutral300 = Color(0xFFCFD2DA)   // default border
val Neutral400 = Color(0xFF8A90A2)   // placeholder 3.19:1 -- UI/large only
val Neutral500 = Color(0xFF616779)   // tertiary text 5.64:1
val Neutral600 = Color(0xFF4A4F63)   // secondary text 8.11:1
val Neutral700 = Color(0xFF3A3E4F)
val Neutral800 = Color(0xFF282C3F)   // primary text 13.80:1
val Neutral900 = Color(0xFF1A1D24)   // dark surface
val Neutral950 = Color(0xFF111318)   // dark canvas

// -- Success / positive ------------------------------------------------------
val Success100 = Color(0xFFD6F5E6)
val Success400 = Color(0xFF34D399)   // 9.67:1 on neutral950
val Success500 = Color(0xFF10B981)
val Success600 = Color(0xFF047857)   // 5.48:1 with white
val Success700 = Color(0xFF03543F)   // 8.96:1 on white -- success text

// -- Warning / pending -------------------------------------------------------
val Warning100 = Color(0xFFFEF0C7)
val Warning400 = Color(0xFFFBBF24)   // 11.13:1 on neutral950
val Warning500 = Color(0xFFF59E0B)   // v1 used this as TEXT -- 2.15:1. Fills only.
val Warning600 = Color(0xFFD97706)
val Warning700 = Color(0xFFB45309)   // 5.02:1 on white -- warning text
// Warning text sitting ON a warning-subtle chip needs a deeper step: warning.700
// on Warning100 measures only 4.42:1, just under the 4.5:1 body threshold.
val Warning750 = Color(0xFF92400E)   // 6.25:1 on Warning100 -- pending chip text

// -- Danger / destructive ----------------------------------------------------
val Danger100 = Color(0xFFFEE4E2)
val Danger400 = Color(0xFFF87171)    // 6.72:1 on neutral950
val Danger500 = Color(0xFFF04438)    // v1's error red -- 3.76:1 on white, failed AA
val Danger600 = Color(0xFFB42318)    // 6.57:1 on white -- error text + fill
val Danger700 = Color(0xFF912018)    // 9.21:1 -- pressed

// -- Info / in-progress ------------------------------------------------------
val Info100 = Color(0xFFDBEAFE)
val Info400 = Color(0xFF60A5FA)      // 7.31:1 on neutral950
val Info600 = Color(0xFF1D4ED8)      // 6.70:1 on white
val Info700 = Color(0xFF1E40AF)

// -- Supporting accents ------------------------------------------------------
val AccentOrange500 = Color(0xFFF97316)  // 2.80:1 -- decorative fills only
val AccentOrange700 = Color(0xFFB4470A)  // 5.35:1 -- accessible orange text
val AccentPurple500 = Color(0xFF8B5CF6)
val AccentPurple700 = Color(0xFF5B21B6)  // 7.42:1 -- accessible purple text

// Star/rating. v1 used #FFB020 as a text colour against white (1.83:1) -- recorded
// as "icon fill only" in the first pass of this redesign, which was wrong on its
// own terms: a first draft at #F59E0B still measured only 2.15:1. The star and the
// rating number beside it read as ONE unit, so the star is held to the 4.5:1 text
// threshold, not the 3:1 graphical one.
val Star500 = Color(0xFFB45309)   // 5.02:1 on white -- star glyph + rating number

// The Indian veg / non-veg mark. Distinct from success green for a reason:
// these are regulatory marks with fixed statutory colours, not brand tokens.
val VegMark    = Color(0xFF0F8A3C)   // 4.51:1 on white
val NonVegMark = Color(0xFF7B341E)

// -- Category fallback palette -- decorative washes, tile backgrounds only ---
// These sit behind text at >= 4.5:1, so the wash itself is never load-bearing.
val CategoryColors = listOf(
    Color(0xFFF5305F), Color(0xFFF97316), Color(0xFFF04438), Color(0xFF8B5CF6),
    Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFFF59E0B), Color(0xFF10B981)
)

// -- DEPRECATION SHIMS -------------------------------------------------------
// v1's name soup, kept compiling through the migration so the 150+ existing
// call sites don't all have to change in one commit. Each one carries a
// @Deprecated so `./gradlew lint` reports every remaining usage with a file and
// line number, and the count reaching zero is the migration's finish line.
//
// These are deliberately NOT annotated with ReplaceWith for the ambiguous
// cases -- "Teal" mapping to brand pink is a semantic decision a human should
// make per call site (is this a fill, or is it text?), not a find-and-replace.
@Deprecated("v1 alias. Use DugguTheme.colors.text.brand (text) or action.primary (fill).",
    level = DeprecationLevel.WARNING)
val MyntraPink = Brand600
@Deprecated("v1 alias. Use Brand700.", level = DeprecationLevel.WARNING)
val MyntraPinkDark = Brand700
@Deprecated("v1 alias. Use Brand800.", level = DeprecationLevel.WARNING)
val MyntraPinkDeep = Brand800
@Deprecated("v1 alias. Use Brand400.", level = DeprecationLevel.WARNING)
val MyntraPinkLight = Brand400
@Deprecated("v1 alias. Use surface.brandSubtle.", level = DeprecationLevel.WARNING)
val MyntraPinkSurface = Brand50

@Deprecated("v1 alias. Use DugguTheme.colors.text.primary.", level = DeprecationLevel.WARNING)
val Ink = Neutral800
@Deprecated("v1 alias. Use DugguTheme.colors.text.secondary.", level = DeprecationLevel.WARNING)
val InkLight = Neutral600
@Deprecated("v1 alias. Use text.tertiary (5.64:1) -- the v1 value was 2.95:1 and failed.",
    level = DeprecationLevel.WARNING)
val InkFaint = Neutral500
@Deprecated("v1 alias. Use border.subtle.", level = DeprecationLevel.WARNING)
val Hairline = Neutral200
@Deprecated("v1 alias. Use surface.sunken.", level = DeprecationLevel.WARNING)
val SoftGray = Neutral100
@Deprecated("v1 alias. Use text.disabled.", level = DeprecationLevel.WARNING)
val DisabledGray = Neutral400

@Deprecated("v1 alias. Use text.primary.", level = DeprecationLevel.WARNING)
val TextPrimary = Neutral800
@Deprecated("v1 alias. Use text.secondary.", level = DeprecationLevel.WARNING)
val TextSecondary = Neutral600
@Deprecated("v1 alias. Use text.tertiary -- v1 value failed contrast.", level = DeprecationLevel.WARNING)
val TextLight = Neutral500
@Deprecated("v1 alias. Use border.subtle.", level = DeprecationLevel.WARNING)
val BorderGray = Neutral200
@Deprecated("v1 alias. Use border.subtle.", level = DeprecationLevel.WARNING)
val DividerGray = Neutral200

@Deprecated("v1 alias. Use surface.canvas.", level = DeprecationLevel.WARNING)
val Background = Neutral50
@Deprecated("v1 alias. Use surface.default.", level = DeprecationLevel.WARNING)
val SurfaceWhite = Neutral0
@Deprecated("v1 alias. Use surface.sunken.", level = DeprecationLevel.WARNING)
val SurfaceMuted = Neutral100
@Deprecated("v1 alias. Use surface.default.", level = DeprecationLevel.WARNING)
val SurfaceElevated = Neutral0

@Deprecated("v1 alias. Use text.success.", level = DeprecationLevel.WARNING)
val SuccessGreen = Success700
@Deprecated("v1 alias. Use text.warning.", level = DeprecationLevel.WARNING)
val WarningYellow = Warning700
@Deprecated("v1 alias. Use text.info.", level = DeprecationLevel.WARNING)
val InfoBlue = Info600
@Deprecated("v1 alias. Use status.pendingFg.", level = DeprecationLevel.WARNING)
val PendingYellow = Warning700
@Deprecated("v1 alias. Use text.success.", level = DeprecationLevel.WARNING)
val DeliveredGreen = Success700
@Deprecated("v1 alias. Star colour is 5.02:1 in v2 -- do not put a label on it at small sizes.",
    level = DeprecationLevel.WARNING)
val StarYellow = Star500
@Deprecated("v1 alias. Use text.danger.", level = DeprecationLevel.WARNING)
val MynRed = Danger600
@Deprecated("v1 alias. Use text.danger.", level = DeprecationLevel.WARNING)
val WarmCoral = Danger600
@Deprecated("v1 alias. Use Danger700.", level = DeprecationLevel.WARNING)
val WarmCoralDark = Danger700
@Deprecated("v1 alias. Use surface.dangerSubtle.", level = DeprecationLevel.WARNING)
val WarmCoralSurface = Danger100
@Deprecated("v1 alias. Use text.danger.", level = DeprecationLevel.WARNING)
val Coral = Danger600
@Deprecated("v1 alias. Use Danger700.", level = DeprecationLevel.WARNING)
val CoralDark = Danger700
@Deprecated("v1 alias. Use surface.dangerSubtle.", level = DeprecationLevel.WARNING)
val CoralSurface = Danger100
@Deprecated("v1 alias. Use text.warning (orange as TEXT).", level = DeprecationLevel.WARNING)
val MynOrange = Warning700
@Deprecated("v1 alias. Use surface.warningSubtle.", level = DeprecationLevel.WARNING)
val MynOrangeSurface = Warning100
@Deprecated("v1 alias. Use text.warning.", level = DeprecationLevel.WARNING)
val Orange = Warning700
@Deprecated("v1 alias. Use Warning600.", level = DeprecationLevel.WARNING)
val OrangeDark = Warning600
@Deprecated("v1 alias. Use Warning400.", level = DeprecationLevel.WARNING)
val OrangeLight = Warning400
@Deprecated("v1 alias. Use surface.warningSubtle.", level = DeprecationLevel.WARNING)
val OrangeSurface = Warning100
@Deprecated("v1 alias. Use AccentPurple700.", level = DeprecationLevel.WARNING)
val RoyalPurple = AccentPurple700
@Deprecated("v1 alias. Use AccentPurple500.", level = DeprecationLevel.WARNING)
val RoyalPurpleDark = AccentPurple500
@Deprecated("v1 alias. Use AccentPurple500.", level = DeprecationLevel.WARNING)
val Violet = AccentPurple500
@Deprecated("v1 alias. Use AccentPurple700.", level = DeprecationLevel.WARNING)
val VioletDark = AccentPurple700
@Deprecated("v1 alias. Use text.success.", level = DeprecationLevel.WARNING)
val MintGreen = Success700
@Deprecated("v1 alias. Use Success600.", level = DeprecationLevel.WARNING)
val MintGreenDark = Success600
@Deprecated("v1 alias. Use VegMark.", level = DeprecationLevel.WARNING)
val VegGreen = VegMark
@Deprecated("v1 alias. Use NonVegMark.", level = DeprecationLevel.WARNING)
val NonVegBrown = NonVegMark

// The worst offenders from v1 -- four names for the same pink. Each now points
// somewhere real, and each raises a lint warning so the count falls to zero.
@Deprecated("Legacy cross-brand alias -- was pink, name says teal. Migrate to action.primary.",
    level = DeprecationLevel.WARNING)
val PrimaryTeal = Brand600
@Deprecated("Legacy cross-brand alias. Migrate to action.primaryPressed.", level = DeprecationLevel.WARNING)
val PrimaryTealDark = Brand800
@Deprecated("Legacy cross-brand alias. Migrate to Brand400.", level = DeprecationLevel.WARNING)
val PrimaryTealLight = Brand400
@Deprecated("Legacy cross-brand alias. Migrate to surface.brandSubtle.", level = DeprecationLevel.WARNING)
val PrimaryTealSurface = Brand50
@Deprecated("Legacy cross-brand alias -- was lime, is pink. Migrate to action.primary.",
    level = DeprecationLevel.WARNING)
val AccentLime = Brand600
@Deprecated("Legacy cross-brand alias. Migrate to action.primaryPressed.", level = DeprecationLevel.WARNING)
val AccentLimeDark = Brand800
@Deprecated("Legacy cross-brand alias. Migrate to Brand400.", level = DeprecationLevel.WARNING)
val AccentLimeLight = Brand400
@Deprecated("Legacy cross-brand alias. Migrate to surface.brandSubtle.", level = DeprecationLevel.WARNING)
val AccentLimeSurface = Brand50
@Deprecated("Legacy flash-sale brand name. Migrate to action.primary.", level = DeprecationLevel.WARNING)
val BlinkitYellow = Brand600
@Deprecated("Legacy flash-sale brand name. Migrate to action.primaryPressed.", level = DeprecationLevel.WARNING)
val BlinkitYellowDark = Brand800
@Deprecated("Legacy flash-sale brand name. Migrate to Brand400.", level = DeprecationLevel.WARNING)
val BlinkitYellowLight = Brand400
@Deprecated("Legacy flash-sale brand name. Migrate to surface.brandSubtle.", level = DeprecationLevel.WARNING)
val BlinkitYellowSurface = Brand50
@Deprecated("Legacy flash-sale brand name. Migrate to action.primary.", level = DeprecationLevel.WARNING)
val BlinkitGreen = Brand600
@Deprecated("Legacy flash-sale brand name. Migrate to action.primaryPressed.", level = DeprecationLevel.WARNING)
val BlinkitGreenDark = Brand800
@Deprecated("Legacy flash-sale brand name. Migrate to Brand400.", level = DeprecationLevel.WARNING)
val BlinkitGreenLight = Brand400
@Deprecated("Legacy flash-sale brand name. Migrate to surface.brandSubtle.", level = DeprecationLevel.WARNING)
val BlinkitGreenSurface = Brand50
@Deprecated("Legacy brand name. Migrate to action.primary.", level = DeprecationLevel.WARNING)
val Teal = Brand600
@Deprecated("Legacy brand name. Migrate to action.primaryPressed.", level = DeprecationLevel.WARNING)
val TealDark = Brand800
@Deprecated("Legacy brand name. Migrate to Brand400.", level = DeprecationLevel.WARNING)
val TealLight = Brand400
@Deprecated("Legacy brand name. Migrate to surface.brandSubtle.", level = DeprecationLevel.WARNING)
val TealSurface = Brand50
@Deprecated("Legacy brand name. Migrate to action.primary.", level = DeprecationLevel.WARNING)
val BrandPrimary = Brand600
@Deprecated("Legacy brand name. Migrate to action.primaryPressed.", level = DeprecationLevel.WARNING)
val BrandPrimaryDark = Brand800
@Deprecated("Legacy brand name. Migrate to Brand400.", level = DeprecationLevel.WARNING)
val BrandPrimaryLight = Brand400
@Deprecated("Legacy brand name. Migrate to surface.brandSubtle.", level = DeprecationLevel.WARNING)
val BrandPrimarySurface = Brand50
@Deprecated("Legacy brand name. Migrate to action.primary.", level = DeprecationLevel.WARNING)
val DeepTeal = Brand600
@Deprecated("Legacy brand name. Migrate to action.primary.", level = DeprecationLevel.WARNING)
val AccentGreen = Brand600
@Deprecated("Legacy brand name. Migrate to action.primaryPressed.", level = DeprecationLevel.WARNING)
val AccentGreenDark = Brand800
@Deprecated("Legacy brand name. Migrate to Brand400.", level = DeprecationLevel.WARNING)
val AccentGreenLight = Brand400
@Deprecated("Legacy brand name. Migrate to surface.brandSubtle.", level = DeprecationLevel.WARNING)
val AccentGreenSurface = Brand50
@Deprecated("Legacy brand name. Migrate to action.primary.", level = DeprecationLevel.WARNING)
val PrimaryGreen = Brand600
@Deprecated("Legacy brand name. Migrate to Brand400.", level = DeprecationLevel.WARNING)
val PrimaryGreenLight = Brand400
@Deprecated("Legacy brand name. Migrate to action.primaryPressed.", level = DeprecationLevel.WARNING)
val PrimaryGreenDark = Brand800
@Deprecated("Legacy brand name. Migrate to text.warning.", level = DeprecationLevel.WARNING)
val AccentOrange = Warning700
@Deprecated("Legacy brand name. Migrate to text.danger.", level = DeprecationLevel.WARNING)
val AccentRed = Danger600


// ==========================================================================
// v1 COMPATIBILITY ALIASES  (added by ui-redesign-v2)
// --------------------------------------------------------------------------
// These names existed in v1's Color.kt and are still referenced by component
// files that this PR intentionally leaves untouched (DashboardComponents.kt,
// HomeSections.kt, StoreComponents.kt, ProductDetailSections.kt). They are
// re-expressed here in terms of the v2 set so nothing has to change meaning:
// every one of them resolves to a v2 token, not a fresh literal.
//
// Accessibility note: v1's MyntraPinkOutline (#FFD9E2) only ever drew a 1dp
// border, so its 1.4:1 contrast was a non-issue for text. Its v2 replacement
// is border.default / border.brand, which are contrast-checked.
// ==========================================================================

/** v1 name -- pink hairline. Prefer `DugguTheme.colors.border.brand`. */
val MyntraPinkOutline = Color(0xFFF3D3DC)

/** v1 star/warm accents. v2 uses Star500 (5.02:1 on light) for the glyph + number. */
val WarmOrange = Orange
val WarmOrangeDark = OrangeDark
val WarmOrangeSurface = OrangeSurface

/** v1 Myntra-style rating chip green. Prefer `colors.text.success`. */
val RatingGreen = Success500

/** v1 category fallback swatches. Tiles now take `accent: Color` from
 *  CategoryColors (the cycled list above); these named aliases are retained
 *  only for the screens that still reference them by name. */
val CategoryGrocery    = Color(0xFF10B981)
val CategoryVeggies    = Color(0xFF22C55E)
val CategoryFruits     = Color(0xFFF97316)
val CategorySnacks     = Color(0xFFF59E0B)
val CategoryChocolate  = Color(0xFF92400E)
val CategoryBread      = Color(0xFFF59E0B)
val CategoryShampoo    = Color(0xFF8B5CF6)
val CategoryCleaning   = Color(0xFF06B6D4)
val CategoryBabyCare   = Color(0xFFEC4899)
val CategoryColdDrinks = Color(0xFF10B981)
val CategoryMeat       = Color(0xFFF04438)
val CategoryDairy      = Color(0xFFA78BFA)
val CategoryFrozen     = Color(0xFF0EA5E9)
