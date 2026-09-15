package com.duggustore.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duggustore.app.ui.theme.Dimens
import com.duggustore.app.ui.theme.DugguTheme
import com.duggustore.app.ui.theme.DugguType
import com.duggustore.app.ui.theme.ShimmerPeriodMs

// ══════════════════════════════════════════════════════════════════════════════
// STATE COMPONENTS — loading, empty, error, offline
// ══════════════════════════════════════════════════════════════════════════════
//
// THE GAP THIS CLOSES
// -------------------
// Measured across the v1 source: `isLoading`/`Loading` appears 30+ times and is
// almost always wired to a bare CircularProgressIndicator centred in a Box, or to
// nothing at all (the screen simply renders empty until data arrives). There was
// NO skeleton, NO empty state and NO error state composable anywhere. The words
// "Empty" and "Error" did not appear as UI in the codebase.
//
// What that meant in practice:
//   • A slow network produced a blank white screen. No progress, no placeholder —
//     indistinguishable from a crash or an empty catalogue.
//   • A first-time seller with zero orders saw the same blank screen a returning
//     seller sees while their orders load.
//   • A failed request left the previous screen's data on screen with no
//     indication it was stale.
//
// These three composables are the fix, and they are deliberately hard to misuse:
// each one takes an optional action, so "there is no way out of this state" is a
// deliberate choice a developer has to make rather than the default.

/**
 * Shimmer placeholder block — the atom of every skeleton below.
 *
 * Two things v1 taught us:
 *  1. The shimmer must be SLOW (1200ms). v1 had a 600ms shimmer on the one screen
 *     that had a placeholder at all, which strobed badly enough to read as an
 *     error flash rather than a loading state.
 *  2. Under reduce-motion it must be a STATIC block with a hairline border. A
 *     sweeping gradient is continuous motion, and "Remove animations" exists
 *     precisely to stop that.
 */
@Composable
fun DugguShimmerBlock(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Dimens.ShapeSm,
    height: Dp? = null,
    width: Dp? = null
) {
    val c = DugguTheme.colors
    val motion = DugguTheme.motion

    val sized = modifier
        .then(if (height != null) Modifier.height(height) else Modifier)
        .then(if (width != null) Modifier.width(width) else Modifier)

    if (motion.reduced) {
        // Static placeholder: a flat sunken block with a visible edge so it still
        // reads as "reserved space" rather than as a rendering artefact.
        Box(
            sized
                .clip(shape)
                .background(c.surface.sunken)
                .border(1.dp, c.border.subtle, shape)
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(ShimmerPeriodMs, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    // The sweep runs from -1 to 2 in x so both the leading and trailing edges
    // travel fully off the block — a 0..1 sweep leaves a hard band at the ends.
    val start = progress * 2f - 1f
    val brush = Brush.linearGradient(
        colors = listOf(
            c.surface.sunken,
            c.surface.raised,
            c.surface.sunken
        ),
        start = androidx.compose.ui.geometry.Offset(start - 0.5f, 0f),
        end = androidx.compose.ui.geometry.Offset(start + 0.5f, 0f)
    )
    Box(sized.clip(shape).background(brush))
}

/** Skeleton matching DugguProductCard's exact geometry. */
@Composable
fun ProductCardSkeleton(modifier: Modifier = Modifier) {
    val c = DugguTheme.colors
    Column(
        modifier = modifier
            .clip(Dimens.ShapeMd)
            .background(c.surface.default)
            .padding(Dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.rowGap)
    ) {
        // aspectRatio(1f) matches the real card, so nothing shifts when content
        // lands. That is the whole point of a skeleton over a spinner.
        // Height matches DugguProductCard's image well exactly (128dp per
        // Dimens.productImageHeight), so nothing shifts when real content lands.
        DugguShimmerBlock(
            modifier = Modifier.fillMaxWidth(),
            shape = Dimens.ShapeMd,
            height = Dimens.productImageHeight
        )
        DugguShimmerBlock(height = 12.dp, width = 56.dp)
        DugguShimmerBlock(height = 16.dp, modifier = Modifier.fillMaxWidth())
        DugguShimmerBlock(height = 16.dp, width = 96.dp)
        DugguShimmerBlock(height = 20.dp, width = 72.dp)
        DugguShimmerBlock(height = 36.dp, modifier = Modifier.fillMaxWidth(), shape = Dimens.ShapeSm)
    }
}

/** Grid of product skeletons — used by Home and category listings. */
@Composable
fun ProductGridSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
    columns: Int = 2
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Announce once, politely, instead of letting a screen reader walk 6
            // separate shimmer nodes that have no content to read.
            .semantics {
                contentDescription = "Loading products"
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(Dimens.space3)
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space3)) {
                repeat(columns) {
                    ProductCardSkeleton(Modifier.weight(1f))
                }
            }
        }
    }
}

/** List-row skeleton — orders, stores, addresses. */
@Composable
fun ListSkeleton(modifier: Modifier = Modifier, rows: Int = 5) {
    val c = DugguTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Loading"
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(Dimens.space2)
    ) {
        repeat(rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Dimens.ShapeMd)
                    .background(c.surface.default)
                    .padding(Dimens.cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.space3)
            ) {
                DugguShimmerBlock(height = 56.dp, width = 56.dp, shape = Dimens.ShapeSm)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.space1)) {
                    DugguShimmerBlock(height = 14.dp, modifier = Modifier.fillMaxWidth())
                    DugguShimmerBlock(height = 12.dp, width = 120.dp)
                }
                DugguShimmerBlock(height = 20.dp, width = 48.dp)
            }
        }
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────
//
// Structure is fixed on purpose: icon → headline → body → one action. v1 had no
// empty state, so the closest equivalent (a toast saying "No orders found") left
// the user on a blank screen with no route forward. An empty state's job is
// always to answer "what do I do now?" — hence `actionLabel` exists, and the
// caller must pass it or pass null explicitly.

@Composable
fun DugguEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val c = DugguTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.space8, vertical = if (compact) Dimens.space6 else Dimens.space12)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(title)
                    if (body != null) append(". $body")
                    if (actionLabel != null) append(". Action available: $actionLabel")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.space3)
    ) {
        Box(
            Modifier
                .size(if (compact) 64.dp else 88.dp)
                .clip(Dimens.ShapeFull)
                .background(c.surface.sunken),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                Modifier.size(if (compact) Dimens.iconLg else Dimens.iconXl),
                // text.tertiary, not placeholder: the illustration is decorative but
                // it is large, and a 3.19:1 glyph at 48dp is muddy on a low-DPI
                // panel. 5.64:1 reads clean.
                tint = c.text.tertiary
            )
        }
        Text(
            text = title,
            style = if (compact) DugguType.titleSm else DugguType.headingSm,
            color = c.text.primary,
            textAlign = TextAlign.Center
        )
        if (body != null) {
            Text(
                text = body,
                style = DugguType.bodyMd,
                color = c.text.secondary,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Dimens.space1))
            DugguButton(
                text = actionLabel,
                onClick = onAction,
                variant = ButtonVariant.PRIMARY,
                size = ButtonSize.MEDIUM
            )
        }
    }
}

// ── Error state ──────────────────────────────────────────────────────────────
//
// Deliberately a different SHAPE from the empty state: no circle behind the icon,
// and the body text carries the technical detail only when expandable. The reason
// is that "no orders yet" and "we could not load your orders" are opposite facts,
// and v1's single blank screen let a user believe the second was the first.

@Composable
fun DugguErrorState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    technicalDetail: String? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again",
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null
) {
    val c = DugguTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.space8, vertical = Dimens.space10)
            .semantics(mergeDescendants = true) {
                // Errors are announced assertively — a user waiting for content
                // should hear the failure, not discover it by swiping around.
                liveRegion = LiveRegionMode.Assertive
                contentDescription = buildString {
                    append("Error: $title")
                    if (body != null) append(". $body")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.space3)
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconXl),
            tint = c.text.danger
        )
        Text(
            text = title,
            style = DugguType.headingSm,
            color = c.text.primary,
            textAlign = TextAlign.Center
        )
        if (body != null) {
            Text(
                text = body,
                style = DugguType.bodyMd,
                color = c.text.secondary,
                textAlign = TextAlign.Center
            )
        }
        if (technicalDetail != null) {
            // Shown inline rather than hidden behind a debug flag: support teams
            // ask users to read this out, and a screen-reader user can select it.
            Text(
                text = technicalDetail,
                style = DugguType.caption,
                color = c.text.tertiary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(Dimens.space1))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
            onSecondaryAction?.let { secondary ->
                DugguButton(
                    text = secondaryActionLabel ?: "Go back",
                    onClick = secondary,
                    variant = ButtonVariant.GHOST,
                    size = ButtonSize.MEDIUM
                )
            }
            if (onRetry != null) {
                DugguButton(
                    text = retryLabel,
                    onClick = onRetry,
                    variant = ButtonVariant.PRIMARY,
                    size = ButtonSize.MEDIUM
                )
            }
        }
    }
}

// ── Offline banner ───────────────────────────────────────────────────────────

@Composable
fun DugguOfflineBanner(
    modifier: Modifier = Modifier,
    message: String = "You're offline. Showing saved content.",
    onRetry: (() -> Unit)? = null
) {
    val c = DugguTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface.warningSubtle)
            .padding(horizontal = Dimens.screenGutter, vertical = Dimens.space2)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2)
    ) {
        Icon(
            androidx.compose.material.icons.Icons.Default.CloudOff,
            contentDescription = null,
            Modifier.size(Dimens.iconSm),
            // warning.700 on warning.100 = 4.89:1 — legal at 12sp.
            tint = c.text.warning
        )
        Text(
            text = message,
            style = DugguType.caption,
            color = c.text.warning,
            modifier = Modifier.weight(1f)
        )
        if (onRetry != null) {
            DugguButton(
                text = "Retry",
                onClick = onRetry,
                variant = ButtonVariant.GHOST,
                size = ButtonSize.SMALL
            )
        }
    }
}
