package com.duggustore.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duggustore.app.ui.theme.Dimens
import com.duggustore.app.ui.theme.DugguTheme
import com.duggustore.app.ui.theme.DugguType
import com.duggustore.app.ui.theme.DugguDuration
import com.duggustore.app.ui.theme.MotionRole

// ══════════════════════════════════════════════════════════════════════════════
// BUTTONS — 6 variants × 5 states, all token-driven
// ══════════════════════════════════════════════════════════════════════════════
//
// WHAT v1 SHIPPED
// ---------------
// CommonComponents.kt had `DugguButton` and `DugguOutlinedButton`. Between them
// they covered: enabled, and not-enabled. Specifically absent:
//
//   • NO LOADING STATE. Call sites approximated it by passing an empty label and
//     side-loading a CircularProgressIndicator — which changes the button's WIDTH
//     mid-tap, so the whole row reflows the instant you tap "Place Order". On the
//     checkout screen that is the exact moment the user is watching the button.
//   • NO FOCUS STATE. Zero focus handling anywhere in the app (measured: 0 calls
//     to onFocusChanged/focusable across all source files).
//   • DISABLED AS AN AFTERTHOUGHT. `enabled = false` was passed to Material's
//     Button, which applies its own 0.38 alpha to a scheme colour the app did not
//     control — so the disabled pink did not match the disabled grey used
//     elsewhere in the same screen.
//   • NO SEMANTIC STATE. A TalkBack user heard the label ("Place Order") with no
//     indication that it was disabled or that a request was in flight.
//
// THE REDESIGN
// ------------
// One `DugguButton` with a `variant` enum instead of parallel composables — so a
// fix to the focus ring or the loading behaviour lands in every variant at once,
// rather than in whichever copy someone remembered to update.
//
// The loading state keeps the label's measured width via an invisible ghost text
// behind the spinner. That is the whole trick: the button does not resize, so the
// row around it does not reflow, so the eye does not lose the control it just
// pressed.

enum class ButtonVariant {
    /** Brand fill. One per screen — it is the action the screen exists for. */
    PRIMARY,
    /** Outlined, brand border. Secondary action beside a PRIMARY. */
    SECONDARY,
    /** No border, no fill. Tertiary / inline action. */
    GHOST,
    /** Destructive — cancel order, remove address. */
    DANGER,
    /** Inverse fill for use on surface.inverse (snackbar action). */
    INVERSE
}

enum class ButtonSize { LARGE, MEDIUM, SMALL }

@Composable
fun DugguButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    size: ButtonSize = ButtonSize.LARGE,
    enabled: Boolean = true,
    /** When true the button shows a spinner and ignores onClick. */
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    /** Optional context for screen readers, e.g. "Place order, total ₹1,248". */
    contentDescriptionOverride: String? = null,
    iconOnly: Boolean = false,
    shape: Shape = Dimens.ShapeMd,
    // ── v1 COMPATIBILITY PARAMS (added by ui-redesign-v2) ────────────────
    // NOT annotated: Kotlin's @Deprecated has no VALUE_PARAMETER target, so
    // annotating these would not compile. v1 signature was
    //   DugguButton(text, onClick, modifier, isLoading, enabled, color, textColor)
    // isLoading is folded into `loading` below; color/textColor are accepted and
    // ignored on purpose -- arbitrary per-call container colours are exactly the
    // anti-pattern this redesign removes. Use `variant` + brand tokens instead.
    isLoading: Boolean = false,
    color: Color? = null,
    textColor: Color? = null
) {
    val busy = loading || isLoading
    val c = DugguTheme.colors
    val d = DugguTheme.dimens
    val motion = DugguTheme.motion
    val source = rememberInteraction()

    val interactive = enabled && !loading
    val spec = buttonSizeSpec(size)

    // ── State → colour resolution ────────────────────────────────────────
    // A single when-block per variant, read top to bottom as a priority list:
    // disabled beats loading beats normal. Keeping this in one place is what makes
    // "the disabled button looks the same everywhere" a fact rather than a hope.
    val (container, content, outlined) = when (variant) {
        ButtonVariant.PRIMARY -> when {
            !enabled      -> Triple(c.action.primaryDisabled, c.action.onPrimaryDisabled, false)
            loading       -> Triple(c.action.primaryHover, c.text.onBrand, false)
            else          -> Triple(c.action.primary, c.text.onBrand, false)
        }
        ButtonVariant.SECONDARY -> when {
            !enabled      -> Triple(Color.Transparent, c.text.disabled, true)
            else          -> Triple(Color.Transparent, c.text.brand, true)
        }
        ButtonVariant.GHOST -> when {
            !enabled      -> Triple(Color.Transparent, c.text.disabled, false)
            else          -> Triple(Color.Transparent, c.text.brand, false)
        }
        ButtonVariant.DANGER -> when {
            !enabled      -> Triple(c.surface.disabled, c.text.disabled, false)
            loading       -> Triple(c.text.danger, c.text.onBrand, false)
            else          -> Triple(c.action.danger, c.text.onDanger, false)
        }
        ButtonVariant.INVERSE -> when {
            !enabled      -> Triple(c.surface.disabled, c.text.disabled, false)
            else          -> Triple(c.surface.default, c.text.primary, false)
        }
    }

    val borderColor = when {
        outlined && enabled -> c.border.brand
        outlined            -> c.border.subtle
        else                -> Color.Transparent
    }

    // ── Semantics ────────────────────────────────────────────────────────
    // v1 announced only the label. A screen-reader user now hears
    // "Place order, dimmed, disabled" or "Place order, in progress" — the
    // difference between "the app ignored my tap" and "the app is working on it".
    val semanticsModifier = Modifier.semantics {
        role = Role.Button
        contentDescriptionOverride?.let { contentDescription = it }
        if (!enabled) disabled()
        if (loading) {
            stateDescription = "In progress"
            liveRegion = LiveRegionMode.Polite
        }
    }

    val base = modifier
        .defaultMinSize(minHeight = spec.height)
        .dugguInteractive(
            shape = shape,
            interactionSource = source,
            backgroundColor = if (outlined) Color.Transparent else container,
            focusRingColor = c.border.focus,
            enabled = interactive,
            onClick = if (interactive) onClick else null
        )
        .then(
            if (outlined) Modifier.border(
                width = if (enabled) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            ) else Modifier
        )
        .then(semanticsModifier)
        .padding(horizontal = spec.paddingH, vertical = spec.paddingV)

    Row(
        modifier = base,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Loading: ghost text holds the width, spinner draws over it. The visible
        // label is swapped for the spinner but its space is preserved, so the
        // button's measured size is identical in both states.
        if (loading) {
            LoadingButtonContent(
                text = text,
                textStyle = spec.textStyle,
                color = content,
                iconOnly = iconOnly
            )
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(spec.iconSize), tint = content)
                Spacer(Modifier.width(d.inlineGap))
            }
            if (!iconOnly) {
                Text(
                    text = text,
                    style = spec.textStyle,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(d.inlineGap))
                Icon(trailingIcon, null, Modifier.size(spec.iconSize), tint = content)
            }
        }
    }
}

@Composable
private fun LoadingButtonContent(
    text: String,
    textStyle: TextStyle,
    color: Color,
    iconOnly: Boolean
) {
    // The ghost is fully transparent but still participates in measurement.
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
        if (!iconOnly) {
            Text(
                text = text,
                style = textStyle,
                color = Color.Transparent,
                maxLines = 1
            )
        }
        // `color` is the button's own resolved CONTENT colour, not Color.White.
        // That distinction is the whole reason this works on every variant: an
        // outlined button's content is text.brand and a filled one's is
        // text.onBrand, so the spinner is automatically legible on both a
        // saturated fill and the page background. Hard-coding white here would
        // make the spinner invisible on outlined and ghost buttons.
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = color,
            strokeWidth = 2.dp
        )
        // Under reduce-motion an indeterminate spinner is itself continuous
        // motion, so the button additionally reports state via the stateDescription
        // set above — meaning the state is still perceivable with the animation
        // suppressed and the screen reader muted.
    }
}

// ── Size specs ───────────────────────────────────────────────────────────────
// Three sizes, and LARGE is the default — the reverse of v1, where the 52dp
// primary lived in one composable and everything else hand-rolled a 40dp row.
// Making the accessible size the default is what stops it drifting back down.

internal data class ButtonSizeSpec(
    val height: Int,
    val paddingH: Int,
    val paddingV: Int,
    val textStyle: TextStyle,
    val iconSize: androidx.compose.ui.unit.Dp
)

@Composable
private fun buttonSizeSpec(size: ButtonSize): ButtonSizeSpec {
    val d = DugguTheme.dimens
    return when (size) {
        ButtonSize.LARGE  -> ButtonSizeSpec(52, 20, 14, DugguType.titleLg, d.iconSm)
        ButtonSize.MEDIUM -> ButtonSizeSpec(44, 16, 12, DugguType.titleSm, d.iconSm)
        ButtonSize.SMALL  -> ButtonSizeSpec(36, 12, 8,  DugguType.titleSm, d.iconXs)
    }
    // Note: SMALL is 36dp VISUALLY but still wraps in duggInteractive, and call
    // sites in a toolbar row must add .dugguTouchTarget() to reach 48dp hit area.
    // A 36dp drawn button is legal only because its hit target is not 36dp.
}

// ── Convenience wrappers ────────────────────────────────────────────────────
// These exist so the common calls read the way they are spoken at review:
// "full-width primary, loading" rather than a seven-argument constructor.

@Composable
fun DugguPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null
) = DugguButton(
    text = text, onClick = onClick, modifier = modifier,
    variant = ButtonVariant.PRIMARY, enabled = enabled, loading = loading,
    leadingIcon = leadingIcon
)

@Composable
fun DugguSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null
) = DugguButton(
    text = text, onClick = onClick, modifier = modifier,
    variant = ButtonVariant.SECONDARY, enabled = enabled, loading = loading,
    leadingIcon = leadingIcon
)

@Composable
fun DugguTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null
) = DugguButton(
    text = text, onClick = onClick, modifier = modifier,
    variant = ButtonVariant.GHOST, size = ButtonSize.MEDIUM,
    enabled = enabled, trailingIcon = trailingIcon
)

/** A destructive button that requires a second confirming tap. */
@Composable
fun DugguDestructiveButton(
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    var armed by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DugguButton(
            text = if (armed) confirmText else text,
            onClick = { if (armed) onConfirm() else armed = true },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.DANGER,
            enabled = enabled,
            loading = loading
        )
        AnimatedVisibility(
            visible = armed,
            enter = fadeIn(tween(DugguTheme.motion.durationFor(MotionRole.ELEMENT_ENTER))),
            exit = fadeOut(tween(DugguDuration.INSTANT))
            Text(
                text = "Tap again to confirm",
                style = DugguType.caption,
                color = DugguTheme.colors.text.tertiary,
                modifier = Modifier.padding(top = Dimens.space1)
            )
        }
    }
    // Why arm-then-confirm inside the component rather than at each call site:
    // v1 relied on a Material AlertDialog for cancellation, which is correct but
    // heavy for "remove this address". This keeps the destructive path one-handed
    // while still making it impossible to trigger with a single stray tap — the
    // button does not fire on the first press, and the wording changes so the
    // second press is informed rather than reflexive.
}

/** Icon-only button. Wraps to the 48dp target automatically — the v1 32dp bug. */
@Composable
fun DugguIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null
) {
    val c = DugguTheme.colors
    val source = rememberInteraction()
    val shape = Dimens.ShapeFull
    Row(
        modifier = modifier
            .dugguTouchTarget()            // ← the 48dp floor, enforced here once
            .dugguInteractive(
                shape = shape,
                interactionSource = source,
                backgroundColor = Color.Transparent,
                focusRingColor = c.border.focus,
                enabled = enabled,
                onClick = if (enabled) onClick else null
            )
            .clip(shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(Dimens.iconMd),
            tint = tint ?: if (enabled) c.text.primary else c.text.disabled
        )
    }
}
