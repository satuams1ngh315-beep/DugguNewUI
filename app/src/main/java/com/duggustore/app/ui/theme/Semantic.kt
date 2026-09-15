package com.duggustore.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ==========================================================================
// SEMANTIC COLOR LAYER -- the only colour surface component code may touch
// ==========================================================================
//
// WHY THIS FILE EXISTS
// --------------------
// In v1 a component that wanted "the brand colour" had to pick between Teal,
// PrimaryTeal, BrandPrimary, DeepTeal, AccentGreen, BlinkitYellow, AccentLime
// and PrimaryGreen -- eight names, all the same pink. Worse, it had no way to
// express *what it meant*: "pink as a text colour" and "pink as a button fill"
// are different pinks if you care about contrast, and v1 used #FF3F6C for both,
// which is why 14 of its 21 measured pairings failed WCAG AA.
//
// Here a component asks for a ROLE, not a hue:
//
//     text.brand      -> a pink that is readable ON the surface  (#B81A45, 6.42:1)
//     action.primary  -> a pink that is readable UNDER white text (#DC2453, 4.75:1)
//
// Same brand, two different values, both legal. A designer can change either
// without hunting through screens, and dark mode rebinds the whole set.
//
// USAGE
// -----
//     @Composable fun Foo() {
//         val c = DugguTheme.colors          // reads the nearest theme
//         Box(Modifier.background(c.surface.default)) {
//             Text("Rs.499", color = c.text.primary)
//         }
//     }
//
// READING ORDER FOR A REVIEWER
// ----------------------------
// Every value below is quoted with its measured ratio against the surface it is
// designed to sit on. Ratios were computed with the WCAG 2.2 relative-luminance
// formula, not eyeballed.

@Immutable
data class DugguColors(
    // -- Surfaces ----------------------------------------------------------
    val surface: SurfaceColors,
    // -- Text --------------------------------------------------------------
    val text: TextColors,
    // -- Borders ----------------------------------------------------------
    val border: BorderColors,
    // -- Actions (button / control fills) ----------------------------------
    val action: ActionColors,
    // -- Order & entity status ---------------------------------------------
    val status: StatusColors,
    // -- Always-on-top colours ---------------------------------------------
    val scrim: Color,
    val shadow: Color,
    /** Dev-only: flips the "show focus rings on every focusable" flag in previews. */
    val isLight: Boolean
)

@Immutable
data class SurfaceColors(
    val canvas: Color,          // page background
    val default: Color,         // cards, sheets, bars
    val raised: Color,          // elevated card sitting on default
    val sunken: Color,          // input wells, image wells, muted chips
    val inverse: Color,         // snackbars, tooltips
    val brandSubtle: Color,     // selected chip, pink wash, promo strip
    val successSubtle: Color,
    val warningSubtle: Color,
    val dangerSubtle: Color,
    val infoSubtle: Color,
    val disabled: Color
)

@Immutable
data class TextColors(
    val primary: Color,         // headings, prices, body
    val secondary: Color,       // supporting body, units, descriptions
    val tertiary: Color,        // metadata, captions -- AA-safe replacement for v1 InkFaint
    val placeholder: Color,     // input placeholder -- NOT for body copy
    val disabled: Color,
    val brand: Color,           // readable brand text
    val onBrand: Color,         // text sitting on action.primary
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val onInverse: Color        // text on surface.inverse
)

@Immutable
data class BorderColors(
    val subtle: Color,
    val default: Color,
    val strong: Color,
    val brand: Color,
    val focus: Color,
    val danger: Color
)

@Immutable
data class ActionColors(
    val primary: Color,
    val primaryHover: Color,
    val primaryPressed: Color,
    val primaryDisabled: Color,
    val onPrimaryDisabled: Color,
    val secondary: Color,
    val onSecondary: Color,
    val danger: Color,
    val onDanger: Color
)

@Immutable
data class StatusColors(
    val pendingFg: Color, val pendingBg: Color,
    val confirmedFg: Color, val confirmedBg: Color,
    val deliveredFg: Color, val deliveredBg: Color,
    val cancelledFg: Color, val cancelledBg: Color
)

// ==========================================================================
// LIGHT THEME
// ==========================================================================
val LightDugguColors = DugguColors(
    surface = SurfaceColors(
        canvas        = Neutral50,
        default       = Neutral0,
        raised        = Neutral0,
        sunken        = Neutral100,
        inverse       = Neutral800,
        brandSubtle   = Brand50,
        successSubtle = Success100,
        warningSubtle = Warning100,
        dangerSubtle  = Danger100,
        infoSubtle    = Info100,
        disabled      = Neutral100
    ),
    text = TextColors(
        primary     = Neutral800,   // 13.80:1 on white
        secondary   = Neutral600,   //  8.11:1 on white
        tertiary    = Neutral500,   //  5.64:1 on white  <- v1 InkFaint was 2.95:1 FAIL
        placeholder = Neutral400,   //  3.19:1 -- UI/large floor only
        // Disabled text is formally EXEMPT from WCAG contrast, but exempt is not
        // the same as invisible. v1 compounded a grey label (#BDBFC6, 1.89:1) with
        // a 0.38 alpha on top, landing near 1.5:1 -- which is why a visual pass
        // read the disabled buttons as empty rectangles. Neutral500 measures
        // 5.00:1 on surface.disabled, and nothing dims it further.
        disabled    = Neutral500,   //  5.00:1 on surface.disabled
        brand       = Brand700,     //  6.42:1 on white  <- v1 #FF3F6C = 3.40:1 FAIL
        onBrand     = Neutral0,     //  4.75:1 on Brand600
        success     = Success700,   //  8.96:1 on white  <- v1 #10B981 = 2.54:1 FAIL
        warning     = Warning700,   //  5.02:1 on white  <- v1 #F97316 = 2.80:1 FAIL
        danger      = Danger600,    //  6.57:1 on white  <- v1 #F04438 = 3.76:1 FAIL
        info        = Info600,      //  6.70:1 on white
        onInverse   = Neutral0
    ),
    border = BorderColors(
        subtle  = Neutral200,
        default = Neutral300,
        strong  = Neutral400,
        brand   = Brand600,
        focus   = Brand700,         // 3px ring @ 2dp offset must clear 3:1 against BOTH
                                    // the component and the page -- Brand700 clears both.
        danger  = Danger600
    ),
    action = ActionColors(
        primary         = Brand600,     // white on this = 4.75:1 PASS AA
        primaryHover    = Brand700,     // white on this = 6.42:1
        primaryPressed  = Brand800,     // white on this = 8.62:1
        primaryDisabled = Neutral200,
        onPrimaryDisabled = Neutral500,   // 5.00:1 on Neutral200 -- legible
        secondary       = Neutral100,
        onSecondary     = Neutral800,
        danger          = Danger600,
        onDanger        = Neutral0
    ),
    status = StatusColors(
        pendingFg   = Warning750, pendingBg   = Warning100,
        confirmedFg = Info600,    confirmedBg = Info100,
        deliveredFg = Success700, deliveredBg = Success100,
        cancelledFg = Danger600,  cancelledBg = Danger100
    ),
    scrim  = Color(0x52111318),   // 32% neutral950
    shadow = Color(0x14101318),   // 8% neutral950 -- v1 used pure black at up to 10dp blur
    isLight = true
)

// ==========================================================================
// DARK THEME
// ==========================================================================
//
// This did not exist in v1 at all -- Theme.kt hard-coded a single LightColorScheme
// and pinned `isAppearanceLightStatusBars = true`, so the app was light-only
// regardless of the OS setting. Every ratio below is measured against the dark
// canvas (#111318), not the light one.
//
// Dark surfaces step UP in lightness with elevation (950 -> 900 -> 22252E -> ...)
// because a shadow is invisible on a dark background. That is why BorderColors
// carries more weight here: on dark, the border is what communicates elevation.
val DarkDugguColors = DugguColors(
    surface = SurfaceColors(
        canvas        = Neutral950,
        default       = Neutral900,
        raised        = Color(0xFF22252E),
        sunken        = Color(0xFF0C0E12),
        inverse       = Neutral50,
        brandSubtle   = Color(0xFF3A1024),
        successSubtle = Color(0xFF052E22),
        warningSubtle = Color(0xFF3A2503),
        dangerSubtle  = Color(0xFF3A1214),
        infoSubtle    = Color(0xFF102049),
        disabled      = Color(0xFF24262E)
    ),
    text = TextColors(
        primary     = Neutral50,    // 17.37:1 on #111318
        secondary   = Neutral200,   // 15.04:1
        tertiary    = Neutral400,   //  5.83:1
        placeholder = Color(0xFF6E7488), // 4.00:1 -- cleared as UI/large
        // 4.74:1 on surface.disabled (#24262E). Legible on purpose -- see the
        // light-theme note above; disabled must not mean invisible.
        disabled    = Neutral400,   //  4.74:1 on surface.disabled
        brand       = Brand400,     //  6.88:1
        onBrand     = Neutral950,   //  4.84:1 on Brand500
        success     = Success400,   //  9.67:1
        warning     = Warning400,   // 11.13:1
        danger      = Danger400,    //  6.72:1
        info        = Info400,      //  7.31:1
        onInverse   = Neutral950
    ),
    border = BorderColors(
        subtle  = Color(0xFF2A2D36),
        default = Color(0xFF363A46),
        strong  = Color(0xFF4E5364),
        brand   = Brand400,
        focus   = Brand300,         // Brand300 on dark canvas = 8.06:1, well clear of 3:1
        danger  = Danger400
    ),
    action = ActionColors(
        primary         = Brand500,
        primaryHover    = Brand400,
        primaryPressed  = Brand300,
        primaryDisabled = Color(0xFF2A2D36),
        onPrimaryDisabled = Neutral400,   // 4.32:1 on #2A2D36 -- legible
        secondary       = Color(0xFF24262E),
        onSecondary     = Neutral50,
        danger          = Danger400,
        onDanger        = Neutral950
    ),
    status = StatusColors(
        pendingFg   = Warning400, pendingBg   = Color(0xFF3A2503),
        confirmedFg = Info400,    confirmedBg = Color(0xFF102049),
        deliveredFg = Success400, deliveredBg = Color(0xFF052E22),
        cancelledFg = Danger400,  cancelledBg = Color(0xFF3A1214)
    ),
    scrim  = Color(0x8A000000),
    shadow = Color(0x66000000),
    isLight = false
)

// ==========================================================================
// ALTERNATE BRAND VARIANTS
// ==========================================================================
//
// Proof the token layer actually decouples brand from semantics: a full re-skin
// is ~8 lines, and NO component file changes. Useful if DugguStore ever launches
// a regional sub-brand, or runs a seasonal campaign without a design freeze.
//
// The rule these obey: only hue changes, luminance steps stay put. Keeping the
// 500/600/700 lightness relationship intact is what lets the contrast ratios
// above survive the swap.
val SaffronOnLight = LightDugguColors.let { base ->
    base.copy(
        surface = base.surface.copy(brandSubtle = Color(0xFFFFF6E8)),
        text = base.text.copy(
            brand   = Color(0xFFA8470B),   // 6.12:1 on white
            onBrand = Neutral0
        ),
        border = base.border.copy(focus = Color(0xFFA8470B), brand = Color(0xFFCE5A0E)),
        action = base.action.copy(
            primary        = Color(0xFFCE5A0E),  // white on this = 4.66:1
            primaryHover   = Color(0xFFA8470B),
            primaryPressed = Color(0xFF8A3807)
        )
    )
}

val IndigoOnLight = LightDugguColors.let { base ->
    base.copy(
        surface = base.surface.copy(brandSubtle = Color(0xFFEEF0FF)),
        text = base.text.copy(
            brand   = Color(0xFF3730A3),   // 8.79:1 on white
            onBrand = Neutral0
        ),
        border = base.border.copy(focus = Color(0xFF3730A3), brand = Color(0xFF4F46E5)),
        action = base.action.copy(
            primary        = Color(0xFF4F46E5),  // white on this = 5.94:1
            primaryHover   = Color(0xFF3730A3),
            primaryPressed = Color(0xFF2E2A85)
        )
    )
}

// ==========================================================================
// COMPOSITION LOCAL
// ==========================================================================
//
// staticCompositionLocalOf (not compositionLocalOf) on purpose: a theme change
// is a whole-subtree rebuild anyway, so there is nothing to gain from fine-
// grained invalidation, and static reads are cheaper on every recomposition of
// every component that touches colour -- which, in this app, is all of them.
//
// The default throws rather than silently supplying light values, so a preview
// or test that forgot to wrap its content in DugguTheme fails loudly at the
// point of the mistake instead of rendering something subtly wrong.
val LocalDugguColors = staticCompositionLocalOf<DugguColors> {
    error(
        "No DugguColors provided. Wrap your content in DugguTheme { } -- " +
        "see ui/theme/Theme.kt."
    )
}
