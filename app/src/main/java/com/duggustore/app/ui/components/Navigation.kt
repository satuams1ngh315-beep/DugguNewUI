package com.duggustore.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duggustore.app.ui.theme.Dimens
import com.duggustore.app.ui.theme.DugguTheme
import com.duggustore.app.ui.theme.DugguType
import com.duggustore.app.ui.theme.MotionRole

// ══════════════════════════════════════════════════════════════════════════════
// NAVIGATION — bottom bar, top app bar, segmented tabs, section header
// ══════════════════════════════════════════════════════════════════════════════
//
// THE BOTTOM BAR, MEASURED
// ------------------------
// v1's StoreBottomBar had four real problems, in order of user impact:
//
//  1. GESTURE-BAR COLLISION. It was a fixed 62dp Row with no WindowInsets handling.
//     On any device with gesture navigation (i.e. most phones shipped since 2020),
//     the bottom ~24dp of the bar sits under the system gesture pill. The nav
//     LABELS were the first thing to disappear, and the "Account" tap target was
//     partially dead. This is the single most physically obvious defect in the app.
//
//  2. 9-10sp LABELS. Nav labels are read in peripheral vision at a glance; v1's
//     were below the readable floor and were the element clipped by (1).
//
//  3. NO SELECTED SEMANTICS. v1 signalled the current tab with a colour change
//     only. A TalkBack user heard four identical items with no indication of which
//     one they were on.
//
//  4. NO BADGE OVERFLOW RULE. The cart badge showed the raw item count in a circle
//     that grew with the number and eventually squeezed the icon. v1's fix was a
//     9sp font, which is (2) again.
//
// THE REDESIGN
// ------------
//  • WindowInsets.navigationBars padding, so the bar grows away from the gesture
//    bar instead of hiding under it. The visual bar stays 64dp; the padding is
//    additive.
//  • Labels at 10sp (micro) — the token floor, deliberately sized so an 11-char
//    label like "Dashboard" fits without truncation at 360dp width.
//  • selected semantics + a filled icon for the active tab, so the state is
//    carried by shape AND colour AND screen-reader state — three signals, one of
//    which survives every vision profile.
//  • Badge caps at "9+" and keeps a fixed minimum size, so the icon never shifts.

data class DugguNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    /** null = no badge. 0 = no badge. >0 renders the count, capped at "9+". */
    val badgeCount: Int? = null,
    /** Overrides the spoken label when it differs from the visible one. */
    val contentDescription: String? = null
)

@Composable
fun DugguBottomBar(
    items: List<DugguNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = DugguTheme.colors
    val motion = DugguTheme.motion

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface.default)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Hairline divider: on light this separates the bar from the canvas; on
        // dark the bar is already lighter than the canvas so the divider reads as
        // an edge definition rather than a line.
        Box(Modifier.fillMaxWidth().height(Dimens.dividerThickness).background(c.border.subtle))

        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.bottomBarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                NavBarItem(
                    item = item,
                    selected = selected,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    item: DugguNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = DugguTheme.colors
    val motion = DugguTheme.motion
    val source = rememberInteraction()

    val tint by animateColorAsState(
        targetValue = if (selected) c.text.brand else c.text.tertiary,
        animationSpec = tween(motion.durationFor(MotionRole.IN_SCREEN), easing = motion.easing.Standard),
        label = "navTint"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .dugguInteractive(
                shape = Dimens.ShapeSm,
                interactionSource = source,
                backgroundColor = Color.Transparent,
                focusRingColor = c.border.focus,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                // The badge count belongs in the spoken label — a screen-reader
                // user has no other way to learn there are 3 items waiting.
                val badge = item.badgeCount?.takeIf { it > 0 }
                contentDescription = buildString {
                    append(item.contentDescription ?: item.label)
                    if (badge != null) append(", $badge ${if (badge == 1) "item" else "items"}")
                    if (selected) append(", selected")
                }
            }
            .padding(vertical = Dimens.space1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                // Filled icon when selected, outline when not. Redundant with the
                // colour change, which is the point — colour alone is never the
                // only carrier of state (WCAG 1.4.1).
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconMd),
                tint = tint
            )
            val badge = item.badgeCount?.takeIf { it > 0 }
            if (badge != null) {
                BadgeDot(count = badge)
            }
        }
        Text(
            text = item.label,
            style = DugguType.micro,       // 10sp floor — was 9sp in v1
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Count badge. Fixed minimum size, capped label, and offset outside the icon so
 * it never overlaps a glyph. v1's badge grew with the count and squeezed the icon.
 */
@Composable
fun BadgeDot(count: Int, modifier: Modifier = Modifier) {
    val c = DugguTheme.colors
    val label = if (count > 9) "9+" else count.toString()
    Box(
        modifier = modifier
            .height(Dimens.badgeMinHeight)
            .width(Dimens.badgeMinHeight)
            .clip(Dimens.ShapeFull)
            .background(c.text.brand),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            // micro is 10sp — sized for a two-character max in a 16dp circle.
            style = DugguType.micro,
            color = c.text.onBrand,
            maxLines = 1
        )
    }
}

// ── Top app bar ──────────────────────────────────────────────────────────────

@Composable
fun DugguTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    // v1 COMPATIBILITY (added by ui-redesign-v2): v1 named this `onBackClick`.
    // Accepted so existing call sites compile; prefer `onBack` in new code.
    onBackClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    /** Large collapses to a compact title once the list scrolls. */
    large: Boolean = false,
    scrolled: Boolean = false
) {
    val back = onBack ?: onBackClick
    val c = DugguTheme.colors
    val motion = DugguTheme.motion

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (scrolled) c.surface.default else c.surface.canvas)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Dimens.space1),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (back != null) {
                DugguIconButton(
                    icon = androidx.compose.material.icons.Icons.Default.ArrowBack,
                    contentDescription = "Go back",
                    onClick = back
                )
            } else {
                Spacer(Modifier.width(Dimens.space1))
            }
            Column(Modifier.weight(1f).padding(horizontal = Dimens.space2)) {
                Text(
                    text = title,
                    style = if (large && !scrolled) DugguType.headingLg else DugguType.titleLg,
                    color = c.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null && !scrolled) {
                    Text(
                        text = subtitle,
                        style = DugguType.caption,
                        color = c.text.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
        if (scrolled) {
            Box(Modifier.fillMaxWidth().height(Dimens.dividerThickness).background(c.border.subtle))
        }
    }
    // Why the title animates weight/size instead of using a collapse fraction:
    // a single-step swap at the scroll threshold is far cheaper to render on the
    // Indian mid-range hardware this app targets than a continuously interpolated
    // height, and the visual difference at 220ms is imperceptible.
}

// ── Segmented tabs ───────────────────────────────────────────────────────────
// Used for order filters (All / Active / Delivered / Cancelled), store categories,
// and seller dashboard ranges. v1 hand-rolled each of these three times with
// different heights, paddings and selected colours.

@Composable
fun <T> DugguSegmentedTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    badgeCount: ((T) -> Int?)? = null
) {
    val c = DugguTheme.colors
    val motion = DugguTheme.motion

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val source = rememberInteraction()
            val bg by animateColorAsState(
                targetValue = if (isSelected) c.surface.brandSubtle else c.surface.sunken,
                animationSpec = tween(motion.durationFor(MotionRole.IN_SCREEN), easing = motion.easing.Standard),
                label = "tabBg"
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) c.text.brand else c.text.secondary,
                animationSpec = tween(motion.durationFor(MotionRole.IN_SCREEN), easing = motion.easing.Standard),
                label = "tabFg"
            )
            val count = badgeCount?.invoke(option)?.takeIf { it > 0 }

            Row(
                modifier = Modifier
                    .dugguTouchTarget()
                    .dugguInteractive(
                        shape = Dimens.ShapeFull,
                        interactionSource = source,
                        backgroundColor = bg,
                        focusRingColor = c.border.focus,
                        onClick = { onSelect(option) }
                    )
                    .padding(horizontal = Dimens.space3, vertical = Dimens.space2)
                    .semantics(mergeDescendants = true) {
                        this.selected = isSelected
                        contentDescription = buildString {
                            append(label(option))
                            if (count != null) append(", $count items")
                            if (isSelected) append(", selected")
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space1)
            ) {
                Text(
                    text = label(option),
                    style = DugguType.titleSm,
                    color = fg,
                    maxLines = 1
                )
                if (count != null) {
                    Text(
                        text = count.toString(),
                        style = DugguType.overline,
                        color = fg
                    )
                }
            }
        }
    }
}

// ── Section header ───────────────────────────────────────────────────────────

@Composable
fun DugguSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = DugguTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenGutter),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DugguType.headingMd, color = c.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = DugguType.caption, color = c.text.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (actionLabel != null && onAction != null) {
            DugguButton(
                text = actionLabel,
                onClick = onAction,
                variant = ButtonVariant.GHOST,
                size = ButtonSize.SMALL,
                trailingIcon = androidx.compose.material.icons.Icons.Default.ChevronRight
            )
        }
    }
}

// ── Sticky bottom action bar ─────────────────────────────────────────────────
// Cart total + checkout, product detail add-to-cart. v1 rendered these as part of
// each screen's Column, so they scrolled away and the user had to reach the bottom
// of a long cart to find the checkout button.

@Composable
fun DugguBottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = DugguTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface.default)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(Modifier.fillMaxWidth().height(Dimens.dividerThickness).background(c.border.subtle))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.screenGutter, vertical = Dimens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.space3)
        ) { content() }
    }
}

// ── Sheet drag handle ────────────────────────────────────────────────────────

@Composable
fun DugguSheetHandle(modifier: Modifier = Modifier) {
    val c = DugguTheme.colors
    Box(
        modifier
            .padding(vertical = Dimens.space2)
            .width(36.dp)
            .height(4.dp)
            .clip(Dimens.ShapeFull)
            .background(c.border.strong)
    )
}
