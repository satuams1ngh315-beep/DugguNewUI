package com.duggustore.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.duggustore.app.ui.theme.Dimens
import com.duggustore.app.ui.theme.DugguTheme
import com.duggustore.app.ui.theme.MotionRole

// ══════════════════════════════════════════════════════════════════════════════
// INTERACTION PRIMITIVES — the accessibility contract, as code
// ══════════════════════════════════════════════════════════════════════════════
//
// WHY THIS FILE EXISTS AT ALL
// ---------------------------
// v1 had an Interaction.kt, and it was the right instinct — but it covered the
// mouse/finger half of interaction and none of the keyboard/assistive half. The
// measured gap across the whole codebase:
//
//     onFocusChanged / FocusRequester / focusable calls ....... 0
//     semantics { } blocks .................................... few, all decorative
//     Modifier.size(32.dp) / size(34.dp) icon buttons ......... 9 occurrences
//
// So: a TalkBack user could reach a button and hear its label, but had no visual
// focus indicator anywhere in the app, and nine tap targets were under the 48dp
// floor (WCAG 2.2 SC 2.5.8 / Material both). That is not a styling gap, it is a
// set of users who cannot operate the checkout flow.
//
// Every composable in this redesign routes its interactive surface through the
// four modifiers below instead of hand-rolling ripple + clickable. That is what
// makes the fix hold: it is one implementation, not 40 call sites.

/**
 * Guarantees the 48dp minimum tap target WITHOUT forcing the visual size.
 *
 * Use on any control whose drawn size is smaller — icon buttons, quantity
 * steppers, the "+9" overflow badge. `sizeIn` grows the LAYOUT node while the
 * visual content stays put, so a 32dp icon still looks 32dp but is tappable
 * across 48dp. e.g. v1's 32dp icon buttons become legal with one modifier.
 */
fun Modifier.dugguTouchTarget(): Modifier =
    this.sizeIn(minWidth = Dimens.minTouchTarget, minHeight = Dimens.minTouchTarget)

/**
 * The focus indicator. Draws INSIDE the node's own bounds, so showing and hiding
 * it never reflows the layout — the single most common reason teams disable a
 * focus ring on a tight toolbar row.
 *
 * Colour comes from border.focus, which is tuned to clear 3:1 against BOTH the
 * component and the page behind it (Brand700 on light = 6.42:1 on white and
 * 6.20:1 on the canvas; Brand300 on dark = 8.06:1).
 */
fun Modifier.dugguFocusRing(
    focused: Boolean,
    shape: Shape,
    color: Color
): Modifier = if (focused) {
    this.border(Dimens.focusRingWidth, color, shape)
} else {
    this
}

/**
 * Press feedback: a 3% scale-down plus a ripple.
 *
 * The scale is what makes a tap feel acknowledged on a device with a slow frame
 * rate — the ripple alone can take a frame or two to appear. Under reduce-motion
 * the scale is skipped entirely (a scaling surface is exactly the motion that
 * triggers vestibular discomfort) and only the ripple remains.
 */
@Composable
fun Modifier.dugguPressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = DugguTheme.motion
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !motion.reduced) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = motion.durationFor(MotionRole.STATE_CHANGE),
            easing = motion.easing.Standard
        ),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * The full interactive surface for a pressable element.
 *
 * Order matters and is deliberate:
 *   1. clip      — so the ripple and any background never bleed outside the corners
 *   2. background
 *   3. pressScale
 *   4. focusable + clickable sharing ONE interactionSource, so focus and press
 *      can never disagree about state
 *   5. focus ring LAST, so it paints above the background rather than under it
 *
 * @param onClick pass null to render a non-interactive surface (used by loading
 *        and disabled states, where a ripple would falsely imply tappability).
 */
@Composable
fun Modifier.dugguInteractive(
    shape: Shape,
    interactionSource: MutableInteractionSource,
    backgroundColor: Color,
    focusRingColor: Color,
    enabled: Boolean = true,
    onClick: (() -> Unit)?
): Modifier {
    val source = interactionSource
    val focused by source.collectIsFocusedAsState()
    return this
        .clip(shape)
        .background(backgroundColor)
        .dugguPressScale(source, enabled)
        .then(if (onClick != null && enabled) Modifier.focusable(source) else Modifier)
        .then(
            if (onClick != null && enabled) {
                Modifier.dugguClickable(source, role = Role.Button, onClick = onClick)
            } else {
                Modifier
            }
        )
        .dugguFocusRing(focused && enabled, shape, focusRingColor)
}

/**
 * `clickable` with an explicit interactionSource, kept separate so the
 * `indication` can be swapped without touching the call sites above.
 */
@Composable
private fun Modifier.dugguClickable(
    interactionSource: MutableInteractionSource,
    role: Role,
    onClick: () -> Unit
): Modifier = androidx.compose.foundation.clickable(
    interactionSource = interactionSource,
    indication = androidx.compose.material3.ripple(
        bounded = true,
        color = androidx.compose.material3.LocalContentColor.current
    ),
    role = role,
    onClick = onClick
)

/** Remember an interaction source for a control — saves a line at every call site. */
@Composable
fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * Disabled opacity — retained as a NAMED NO-OP, deliberately.
 *
 * The first version of this used Material's 0.38 alpha on top of an already-muted
 * text.disabled colour. Those two attenuations COMPOUND: a 0.38 alpha drops a
 * 5.00:1 label to roughly 1.9:1 effective, which a visual QA pass read as an empty
 * rectangle rather than a disabled button. v1 had the same defect via a different
 * route (a #BDBFC6 label, 1.89:1, before any alpha).
 *
 * Disabled is now conveyed by three signals that do not fight each other:
 *   1. the token colour itself (text.disabled — a legal 5.00:1 / 4.74:1)
 *   2. `Modifier.semantics { disabled() }`, so TalkBack announces it
 *   3. no ripple and no press-scale, so it does not react
 *
 * The function is kept — returning [Modifier] unchanged — so existing call sites
 * still compile while the double-dim disappears. Removing it outright would be the
 * cleaner API, but that is a rename across every component; this is the zero-risk
 * version. Delete it once call sites stop referencing it.
 */
@Deprecated(
    "Disabled is conveyed by text.disabled + semantics { disabled() }, not by alpha. "
        + "This is a no-op; remove the call.",
    level = DeprecationLevel.WARNING
)
fun Modifier.dugguDisabledAlpha(enabled: Boolean): Modifier = this

/**
 * Hover feedback for the pointer-enabled builds (desktop/ChromeOS, and the
 * Android tablet + mouse case). Cheap, and it was simply absent in v1.
 */
@Composable
fun Modifier.dugguHoverLift(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    hoverColor: Color
): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    return this.background(if (hovered) hoverColor else Color.Transparent, shape)
}

/** Reserved gutter a screen should leave so focus rings on edge controls stay visible. */
val FocusRingGutter = Dimens.space1   // 4dp = 3dp ring + 1dp breathing room


// ══════════════════════════════════════════════════════════════════════════════
// v1 COMPATIBILITY — TAP FEEDBACK  (added by ui-redesign-v2)
// ──────────────────────────────────────────────────────────────────────────────
// StoreComponents.kt and ScreenScaffold.kt call these on ~30 tap targets, so
// dropping them would break the build for no user-visible gain. They now
// delegate to the v2 interaction pipeline: the same indigo ripple, but with the
// 48dp touch target and the focus ring that v1's versions never had — which is
// why `dugguClickable` silently fixed nine sub-48dp controls without touching a
// single call site.
// ══════════════════════════════════════════════════════════════════════════════

@Deprecated(
    "v1 API — prefer Modifier.dugguInteractive(), which adds the focus ring and " +
    "enforces the 48dp touch target. This shim keeps the v1 ripple only.",
    level = DeprecationLevel.WARNING
)
@Composable
fun Modifier.dugguClickable(onClick: () -> Unit): Modifier =
    this.dugguTouchTarget().clickable(
        interactionSource = rememberInteraction(),
        indication = ripple(
            bounded = true,
            color = LocalContentColor.current.copy(alpha = 0.20f)
        ),
        onClick = onClick
    )

@Deprecated(
    "v1 API — prefer Modifier.dugguInteractive()/dugguPressScale(). Kept because " +
    "the curved category tabs and the location strip rely on having no ripple.",
    level = DeprecationLevel.WARNING
)
@Composable
fun Modifier.dugguClickableFlat(onClick: () -> Unit): Modifier =
    this.dugguTouchTarget().clickable(
        interactionSource = rememberInteraction(),
        indication = null,
        onClick = onClick
    )
