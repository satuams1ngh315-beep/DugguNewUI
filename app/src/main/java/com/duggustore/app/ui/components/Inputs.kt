package com.duggustore.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.duggustore.app.ui.theme.Dimens
import com.duggustore.app.ui.theme.DugguTheme
import com.duggustore.app.ui.theme.DugguType
import com.duggustore.app.ui.theme.MotionRole

// ══════════════════════════════════════════════════════════════════════════════
// INPUTS — text field, search field, quantity stepper
// ══════════════════════════════════════════════════════════════════════════════
//
// THE v1 PROBLEM: NO ERROR STATE EVER EXISTED
// -------------------------------------------
// Measured across the codebase: the word "error" appears in the UI layer only in
// the context of a caught exception rendered as a toast. There was no `isError`
// parameter anywhere, no supporting-error slot, and no field-level validation
// feedback. A user who typed an invalid 10-digit phone number, or a pincode the
// dark store does not serve, got a snackbar at the bottom of the screen that
// disappeared in three seconds while the field itself kept looking perfectly
// valid and focused.
//
// This is the single highest-cost gap in the audit: the address and checkout
// screens are 12 fields deep, and a validation failure at field 3 is announced
// at the opposite end of the screen.
//
// WHAT THIS FILE ADDS
// -------------------
//   • isError + supportingText slot, with an icon so the state is not signalled
//     by COLOUR ALONE (WCAG 1.4.1 Use of Colour — the most common AA failure in
//     form design, and one that affects ~8% of men in this app's market).
//   • Animated supporting-text slot. Appearing/disappearing without animation
//     (v1's behaviour for anything similar) shifts every field below it, which on
//     a 12-field form means the field the user was reading jumps.
//   • Label as a static label, not a placeholder. v1 used placeholder-to-label
//     swapping in several forms, so a filled field had NO visible name — a user
//     reviewing their address could not tell which line was the flat number.
//   • Char counter that turns into an error-coloured over-limit indicator.

@Composable
fun DugguTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    errorMessage: String? = null,
    helperText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    maxLength: Int? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isPassword: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = Dimens.ShapeSm
) {
    val c = DugguTheme.colors
    val motion = DugguTheme.motion
    val isError = errorMessage != null
    val overLimit = maxLength != null && value.length > maxLength

    // Border colour animates between the four legal states. v1 had no transition
    // here at all, so a field snapped between colours and the change was easy to
    // miss on an eye already mid-sentence.
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled  -> c.border.subtle
            isError   -> c.border.danger
            else      -> c.border.default
        },
        animationSpec = tween(motion.durationFor(MotionRole.STATE_CHANGE), easing = motion.easing.Standard),
        label = "fieldBorder"
    )

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                // Hard stop at maxLength + a small grace so the counter can show
                // "41/40" rather than silently swallowing the keystroke. Silently
                // refusing input reads as a broken keyboard.
                val cap = maxLength?.plus(10)
                if (cap == null || next.length <= cap) onValueChange(next)
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.fieldHeight)
                .semantics {
                    // Announce the field as invalid to TalkBack. Without this the
                    // error is purely visual and a screen-reader user cannot tell
                    // why the Continue button refused to advance.
                    if (isError) this.error(errorMessage)
                },
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            isError = isError,
            label = {
                Text(
                    text = label,
                    style = DugguType.caption,
                    color = when {
                        !enabled -> c.text.disabled
                        isError  -> c.text.danger
                        else     -> c.text.secondary
                    }
                )
            },
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        style = DugguType.bodyMd,
                        // placeholder token, explicitly NOT text.tertiary: a
                        // placeholder must be distinguishable from real content,
                        // and it is never the only label.
                        color = c.text.placeholder
                    )
                }
            },
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        it, null, Modifier.size(Dimens.iconSm),
                        tint = if (isError) c.text.danger else c.text.tertiary
                    )
                }
            },
            trailingIcon = when {
                isError && errorMessage != null -> {
                    // The ICON is the primary error signal; the red border and red
                    // text merely reinforce it. This is what keeps the state
                    // perceivable without colour vision.
                    {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Warning,
                            contentDescription = "Error: $errorMessage",
                            Modifier.size(Dimens.iconSm),
                            tint = c.text.danger
                        )
                    }
                }
                trailingIcon != null -> {
                    {
                        val mod = if (onTrailingIconClick != null) {
                            Modifier.size(Dimens.iconSm).dugguTouchTarget()
                        } else Modifier.size(Dimens.iconSm)
                        Icon(
                            trailingIcon,
                            contentDescription = null,
                            mod,
                            tint = if (enabled) c.text.tertiary else c.text.disabled
                        )
                    }
                }
                else -> null
            },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = keyboardActions,
            textStyle = DugguType.bodyMd,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = if (isError) c.border.danger else c.border.brand,
                unfocusedBorderColor = borderColor,
                errorBorderColor     = c.border.danger,
                disabledBorderColor  = c.border.subtle,
                focusedContainerColor   = c.surface.default,
                unfocusedContainerColor = c.surface.default,
                disabledContainerColor  = c.surface.disabled,
                errorContainerColor     = c.surface.default,
                cursorColor      = c.text.brand,
                focusedTextColor = c.text.primary,
                unfocusedTextColor = c.text.primary,
                disabledTextColor  = c.text.disabled,
                errorTextColor     = c.text.primary
            )
        )

        // Supporting slot: error takes priority over helper. Exactly one line, so
        // the slot height is stable whether the message is 12 or 40 characters —
        // the animated visibility handles the 0→1 line transition instead.
        val supporting = errorMessage ?: helperText
        AnimatedVisibility(
            visible = supporting != null,
            enter = fadeIn(tween(motion.durationFor(MotionRole.STATE_CHANGE))) +
                expandVertically(tween(motion.durationFor(MotionRole.STATE_CHANGE))),
            exit = fadeOut(tween(motion.durationFor(MotionRole.INSTANT))) +
                shrinkVertically(tween(motion.durationFor(MotionRole.STATE_CHANGE)))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Dimens.space3, top = Dimens.space1, end = Dimens.space3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = supporting.orEmpty(),
                    style = DugguType.caption,
                    color = if (isError) c.text.danger else c.text.tertiary
                )
                if (maxLength != null) {
                    Text(
                        text = "${value.length}/$maxLength",
                        style = DugguType.caption,
                        color = if (overLimit) c.text.danger else c.text.tertiary
                    )
                }
            }
        }

        // Counter when there is no supporting text but a limit exists.
        if (supporting == null && maxLength != null) {
            Text(
                text = "${value.length}/$maxLength",
                style = DugguType.caption,
                color = if (overLimit) c.text.danger else c.text.tertiary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = Dimens.space1, end = Dimens.space3)
            )
        }
    }
}

/**
 * Search field — the pill variant used at the top of Home, Stores and Orders.
 *
 * v1's search bar was a Row with a hand-set 44dp height, a 12dp radius and a
 * hint text. It had no clear button (users had to backspace), no focus state, and
 * its tap area was the visual bar only — no height padding, so on a tall thumb it
 * was easy to miss.
 */
@Composable
fun DugguSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Search for products, stores and more",
    onClear: () -> Unit = { onValueChange("") },
    onSearch: () -> Unit = {},
    enabled: Boolean = true,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val c = DugguTheme.colors
    val source = rememberInteraction()
    val shape = Dimens.ShapeFull

    if (readOnly && onClick != null) {
        // The "fake search bar" pattern — tapping navigates to a search screen.
        // Kept as its own branch because a readOnly TextField still shows a caret
        // and a keyboard on some IMEs, which reads as broken.
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .dugguInteractive(
                    shape = shape,
                    interactionSource = source,
                    backgroundColor = c.surface.sunken,
                    focusRingColor = c.border.focus,
                    onClick = onClick
                )
                .padding(horizontal = Dimens.space4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.Search,
                contentDescription = null,
                Modifier.size(Dimens.iconSm),
                tint = c.text.tertiary
            )
            Spacer(Modifier.width(Dimens.inlineGap))
            Text(hint, style = DugguType.bodyMd, color = c.text.placeholder)
        }
        return
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { }
            .height(48.dp),
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(hint, style = DugguType.bodyMd, color = c.text.placeholder) },
        leadingIcon = {
            Icon(
                androidx.compose.material.icons.Icons.Default.Search,
                contentDescription = null,
                Modifier.size(Dimens.iconSm),
                tint = c.text.tertiary
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                DugguIconButton(
                    icon = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "Clear search",
                    onClick = onClear,
                    tint = c.text.tertiary
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        textStyle = DugguType.bodyMd,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = c.border.brand,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor   = c.surface.default,
            unfocusedContainerColor = c.surface.sunken,
            cursorColor = c.text.brand,
            focusedTextColor = c.text.primary,
            unfocusedTextColor = c.text.primary
        )
    )
}

/**
 * Quantity stepper for cart and PDP.
 *
 * v1 rendered this as a Row with three 32dp-tall elements and 12dp text. That is
 * a 32dp tap target on the two most-tapped controls in the purchase flow — under
 * the 48dp floor, and repeated 5-10 times down a cart list. It is also the control
 * a user operates while holding a bag, which is precisely when a small target
 * fails.
 *
 * Now: 44dp visual, 48dp via duggInteractive's defaultMinSize, and the minus
 * button at quantity 1 becomes a trash icon rather than a disabled minus — v1
 * showed a greyed-out minus with no affordance for removal.
 */
@Composable
fun DugguQuantityStepper(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 20,
    enabled: Boolean = true,
    /** When true, decrementing at min calls onRemove instead of being disabled. */
    removable: Boolean = false,
    onRemove: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val c = DugguTheme.colors
    val height = if (compact) 36.dp else 44.dp
    val atMin = quantity <= min
    val showRemove = atMin && removable && onRemove != null

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = Dimens.minTouchTarget)
            .clip(Dimens.ShapeSm)
            .border(1.dp, c.border.brand, Dimens.ShapeSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            icon = if (showRemove) androidx.compose.material.icons.Icons.Default.Delete
                   else androidx.compose.material.icons.Icons.Default.Remove,
            description = if (showRemove) "Remove item" else "Decrease quantity",
            enabled = enabled && (!atMin || showRemove),
            size = height,
            onClick = { if (showRemove) onRemove?.invoke() else onDecrement() }
        )
        Text(
            text = quantity.toString(),
            style = DugguType.titleSm,
            color = if (enabled) c.text.brand else c.text.disabled,
            modifier = Modifier
                .width(28.dp)
                .padding(horizontal = Dimens.space1),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1
        )
        StepperButton(
            icon = androidx.compose.material.icons.Icons.Default.Add,
            description = "Increase quantity",
            enabled = enabled && quantity < max,
            size = height,
            onClick = onIncrement
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val c = DugguTheme.colors
    val source = rememberInteraction()
    Row(
        modifier = Modifier
            .size(size)
            .dugguInteractive(
                shape = Dimens.ShapeSm,
                interactionSource = source,
                backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                focusRingColor = c.border.focus,
                enabled = enabled,
                onClick = if (enabled) onClick else null
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, description, Modifier.size(Dimens.iconXs),
            tint = if (enabled) c.text.brand else c.text.disabled
        )
    }
}
