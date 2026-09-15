package com.duggustore.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ==========================================================================
// SPACING . RADIUS . ELEVATION . SIZES
// ==========================================================================
//
// v1's Dimens.kt declared six spacing values (4/8/12/16/24 + a 16dp screen pad)
// and then the app used 40 different dp values anyway -- 5, 7, 9, 11, 13, 17, 19,
// 22, 26, 34 dp and so on. Anything with a name and no enforcement drifts.
//
// The fix is not "more tokens". It is a closed 4dp grid, one name per step, and
// a lint rule. There are now exactly 11 spacing steps and every margin in the
// redesign is one of them.
//
// -- THE GRID --------------------------------------------------------------
//   space0   0    flush
//   space1   4    icon<->label, badge inset, hairline gaps
//   space2   8    row gap, chip padding, stackTight
//   space3  12    card padding, stackLoose, input padding
//   space4  16    SCREEN GUTTER, between cards
//   space5  20    wide gutter (home carousels edge-inset)
//   space6  24    section gap
//   space8  32    empty-state / dialog padding
//   space10 40    hero breathing room
//   space12 48    min touch target
//   space16 64    full-bleed illustration
//
// v1's off-grid values map cleanly: 5->4, 6->4 or 8, 7->8, 9->8 or 12, 10->12,
// 11->12, 13->12, 14->16, 15->16, 17->16, 18->16 or 20, 19->20, 22->24,
// 26->24, 34->32.
//
// -- RADIUS ----------------------------------------------------------------
// v1 had three radii (12/18/24) but used 14, 16, 11, 10, 9, 6, 4, 2 and 50 in
// components -- including a card that declared `RoundedCornerShape(14.dp)` and
// then clipped its own image to `RoundedCornerShape(16.dp)`, so the image corners
// visibly poked past the card corners. Six named steps, matched top and bottom.
//
// SHAPE MISMATCH GUARD: when a container rounds at radiusX, its children must not
// exceed radiusX. Shape.xs/sm/md/lg/xl/full are ordered, and the convention is
// that a child uses the SAME step or smaller. The specific v1 bug above is fixed
// by ProductCard using shape.md for the card AND for its image container.

object Dimens {

    // -- Spacing -----------------------------------------------------------
    val space0  = 0.dp
    val space1  = 4.dp
    val space2  = 8.dp
    val space3  = 12.dp
    val space4  = 16.dp
    val space5  = 20.dp
    val space6  = 24.dp
    val space8  = 32.dp
    val space10 = 40.dp
    val space12 = 48.dp
    val space16 = 64.dp

    // -- Semantic spacing aliases ------------------------------------------
    // Named for the JOB, so a screen reads as intent rather than arithmetic.
    val screenGutter = space4     // left/right page margin -- 16dp
    val screenTop    = space3
    val sectionGap   = space6     // between home sections -- 24dp
    val cardPadding  = space3     // 12dp
    val rowGap       = space2     // 8dp
    val inlineGap    = space2     // icon<->label -- 8dp
    val stackTight   = space1     // 4dp
    val stackLoose   = space3     // 12dp

    // -- Radius ------------------------------------------------------------
    val radiusXs   = 4.dp     // badge, veg mark
    val radiusSm   = 8.dp     // input, chip, stepper
    val radiusMd   = 12.dp    // button, product card
    val radiusLg   = 16.dp    // sheet, panel
    val radiusXl   = 24.dp    // bottom-sheet top, modal
    val radiusFull = 999.dp   // pill, avatar, search bar

    val ShapeXs   = RoundedCornerShape(radiusXs)
    val ShapeSm   = RoundedCornerShape(radiusSm)
    val ShapeMd   = RoundedCornerShape(radiusMd)
    val ShapeLg   = RoundedCornerShape(radiusLg)
    val ShapeXl   = RoundedCornerShape(radiusXl)
    val ShapeFull = RoundedCornerShape(radiusFull)

    /** Top-rounded only -- for image wells inside a card. */
    val ShapeMdTop = RoundedCornerShape(topStart = radiusMd, topEnd = radiusMd)
    val ShapeLgTop = RoundedCornerShape(topStart = radiusLg, topEnd = radiusLg)
    /** Bottom-rounded only -- for bottom sheets. */
    val ShapeXlTop = RoundedCornerShape(topStart = radiusXl, topEnd = radiusXl)

    // -- Elevation ---------------------------------------------------------
    // Five levels. In light theme elevation = shadow. In dark theme a shadow is
    // invisible, so elevation is paired with the surface step and border that
    // actually carry it.
    val elevation0 = 0.dp
    val elevation1 = 1.dp    // resting card
    val elevation2 = 2.dp    // raised card
    val elevation3 = 6.dp    // sticky header, FAB
    val elevation4 = 12.dp   // sheet, dialog, snackbar

    // -- Touch & interactive sizes -----------------------------------------
    // WCAG 2.2 SC 2.5.8 Target Size (Minimum) requires 24x24 CSS px; both Apple
    // HIG and Material require 48dp. This app is used one-handed on a bus, so it
    // honours the stricter 48dp -- enforced by Modifier.dugguTouchTarget(), not by
    // memory. See Interaction.kt.
    val minTouchTarget = 48.dp
    val iconButton     = 48.dp   // v1 used 40dp -- below the floor, raised
    val iconButtonSm   = 40.dp   // inside a 48dp hit area
    val controlHeight  = 52.dp   // primary button (v1 had this right)
    val controlHeightSm = 40.dp
    val fieldHeight    = 56.dp   // Material text-field spec
    val bottomBarHeight = 64.dp  // v1: 62dp -- off-grid, and the nav label pushed
                                 // under the system gesture bar on some devices

    // -- Iconography -------------------------------------------------------
    // v1 used 17, 18, 19, 21, 23, 24, 26, 28, 34, 40, 44, 56, 80 dp for icons.
    // Five sizes cover every case in the app.
    val iconXs = 16.dp
    val iconSm = 20.dp
    val iconMd = 24.dp
    val iconLg = 32.dp
    val iconXl = 48.dp

    // -- Content sizing ----------------------------------------------------
    val productImageHeight   = 128.dp
    val cartThumb            = 56.dp   // v1: 48dp -- below the comfortable tap floor
    val avatarMd             = 40.dp
    val avatarLg             = 56.dp
    val badgeDot             = 8.dp
    val badgeMinHeight       = 16.dp   // v1: 15dp with 9sp text -- clipped descenders
    val dividerThickness     = 1.dp    // v1 drew 0.5dp hairlines; below 1dp they
                                       // vanish on xxhdpi and alias to grey mush
    val focusRingWidth       = 3.dp
    val focusRingOffset      = 2.dp

    // -- v1 COMPATIBILITY MEMBERS ------------------------------------------
    // Every v1 member name, re-pointed at its v2 counterpart so the untouched
    // component files keep compiling. New code should use the v2 names.
    val SpaceXs        get() = space1
    val SpaceSm        get() = space2
    val SpaceMd        get() = space3
    val SpaceLg        get() = space4
    val ScreenPadding  get() = screenGutter
    val SectionGap     get() = sectionGap
    val RadiusSm       get() = radiusSm
    val RadiusMd       get() = radiusMd
    val RadiusLg       get() = radiusLg
    val ElevationCard  get() = elevation1
    val ElevationRaised get() = elevation3
    val MinTouchTarget get() = minTouchTarget
    val IconButtonSize get() = iconButton
    val IconSm         get() = iconSm
    val IconMd         get() = iconMd
}

/**
 * Material3 Shapes wiring -- same constructor as v1 so Theme.kt's call site is
 * unchanged, but now the slots carry the real radii so Material's own components
 * (Dialog, BottomSheet, Card) stop inventing their own corners.
 */
val Shapes = Shapes(
    extraSmall = Dimens.ShapeXs,
    small      = Dimens.ShapeSm,
    medium     = Dimens.ShapeMd,
    large      = Dimens.ShapeLg,
    extraLarge = Dimens.ShapeXl
)
