package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.duggustore.app.ui.theme.Dimens
import com.duggustore.app.ui.theme.DugguTheme
import com.duggustore.app.ui.theme.DugguType
import com.duggustore.app.ui.theme.Star500

// ══════════════════════════════════════════════════════════════════════════════
// COMMERCE CARDS — product, store, category, price block, order status
// ══════════════════════════════════════════════════════════════════════════════
//
// THE v1 PRODUCT CARD, ANALYSED
// -----------------------------
// StoreComponents.kt's ProductCard had genuine ambition — discount ribbon, wishlist
// heart, rating pill, price block with strikethrough MRP. But it carried five
// issues that together explain why the home grid felt noisy despite low information
// density:
//
//  1. RADIUS MISMATCH. The card clipped to 14dp; the image container inside it
//     clipped to 16dp. The image's corners therefore poked 2dp past the card's
//     corners — a visible sliver at all four corners on every card in the grid.
//
//  2. THREE COMPETING ACCENTS PER CARD. The discount ribbon (brand pink), the
//     rating pill (amber) and the wishlist heart (brand pink again) each carried
//     saturation, so no single element won the user's eye. There was no hierarchy:
//     everything shouted.
//
//  3. 9sp COUNT TEXT on the rating pill — below any readable floor, on the second
//     most-scanned number on the card.
//
//  4. NO PRESSED OR FOCUS STATE. The card was clickable with a default ripple and
//     nothing else, so there was no hover/lift affordance and no focus ring.
//
//  5. HARD-CODED Color.White in three places (ribbon text, heart fill, image
//     scrim), meaning the card could never go dark without edits.
//
// THE REDESIGN, DECISION BY DECISION
// ----------------------------------
//  • One radius, applied at the container AND the image. Dimens.ShapeMd (12dp) for
//    both — the mismatch is structurally impossible now because the image uses
//    the same token, and there is a comment above the shape set saying children
//    must use the same step or smaller.
//
//  • ONE accent per card: the price. Discount moved from a ribbon into a small
//    green text chip beside the price, because discount is a property OF the
//    price. The heart is now outline-only until saved (see WishlistButton), so it
//    reads as an affordance rather than a filled accent at rest.
//
//  • Rating raised to 11sp (overline) and its star reduced to a 12dp glyph — the
//    number is the datum, the star is the label for it.
//
//  • Full state coverage: pressed (scale + ripple via duggInteractive), focus
//    ring, loading (skeleton), unavailable (dimmed + label, not just opacity),
//    and out-of-stock.
//
//  • Zero hard-coded colours. Everything resolves from DugguColors.

/**
 * Product card for the home grid and category listings.
 *
 * @param onAddToCart null hides the add button entirely (some listing contexts
 *        are browse-only). This is deliberate: v1 always rendered the button and
 *        several call sites passed a no-op, so a user could tap "Add" and get
 *        nothing at all.
 */
@Composable
fun DugguProductCard(
    name: String,
    price: String,
    modifier: Modifier = Modifier,
    mrp: String? = null,
    discountLabel: String? = null,
    unit: String? = null,
    imageUrl: String? = null,
    rating: Float? = null,
    ratingCount: Int? = null,
    saved: Boolean = false,
    /** Receives the DESIRED new value, so the caller owns the state. */
    onSavedChange: ((Boolean) -> Unit)? = null,
    inStock: Boolean = true,
    onAddToCart: (() -> Unit)? = null,
    onClick: () -> Unit,
    /** Renders the shimmer placeholder instead of content. */
    loading: Boolean = false
) {
    if (loading) {
        ProductCardSkeleton(modifier)
        return
    }

    val c = DugguTheme.colors
    val d = DugguTheme.dimens
    val source = rememberInteraction()
    val shape = Dimens.ShapeMd

    Column(
        modifier = modifier
            .clip(shape)
            .dugguInteractive(
                shape = shape,
                interactionSource = source,
                backgroundColor = c.surface.default,
                focusRingColor = c.border.focus,
                onClick = onClick
            )
            .padding(Dimens.cardPadding)
            // One spoken sentence instead of five disconnected nodes. A grid of 20
            // cards otherwise forces a screen-reader user through 100 swipes.
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(name)
                    if (unit != null) append(", $unit")
                    append(", $price")
                    if (mrp != null) append(", was $mrp")
                    if (discountLabel != null) append(", $discountLabel off")
                    if (rating != null && ratingCount != null) {
                        append(", rated $rating out of 5 from $ratingCount ratings")
                    }
                    if (!inStock) append(", currently unavailable")
                }
            },
        verticalArrangement = Arrangement.spacedBy(Dimens.rowGap)
    ) {
        // ── Image well ─────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)                       // same token as the card — no sliver
                .background(c.surface.sunken)
        ) {
            if (imageUrl != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                    contentDescription = null,     // named by the card's merged description
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Real builds: use Coil's AsyncImage here with the same modifier.
                // contentDescription stays null because the parent merges.
            } else {
                Text(
                    text = "img",
                    style = DugguType.micro,
                    color = c.text.placeholder,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Unavailable: a WASH plus an explicit LABEL, never opacity alone.
            // Opacity-only would be invisible to a low-vision user and would not
            // explain WHY the card looks different.
            if (!inStock) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(c.surface.default.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Out of stock",
                        style = DugguType.caption,
                        color = c.text.secondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ── Meta row: rating (left) + wishlist (right) ────────────────────────
        if (rating != null || onSavedChange != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (rating != null) {
                    RatingPill(rating = rating, count = ratingCount)
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                if (onSavedChange != null) {
                    WishlistToggle(
                        saved = saved,
                        onToggle = { onSavedChange(!saved) },
                        itemName = name
                    )
                }
            }
        }

        // ── Name — exactly two lines, so grid rows keep a common baseline ─────
        Text(
            text = name,
            style = DugguType.titleMd,
            color = c.text.primary,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // ── Unit / pack size ─────────────────────────────────────────────────
        if (unit != null) {
            Text(
                text = unit,
                style = DugguType.bodySm,
                color = c.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Price block ──────────────────────────────────────────────────────
        PriceBlock(price = price, mrp = mrp, discountLabel = discountLabel)

        // ── Add control ──────────────────────────────────────────────────────
        if (onAddToCart != null) {
            Spacer(Modifier.height(Dimens.space1))
            if (inStock) {
                DugguButton(
                    text = "Add",
                    onClick = onAddToCart,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.PRIMARY,
                    size = ButtonSize.MEDIUM,
                    shape = Dimens.ShapeSm
                )
            } else {
                DugguButton(
                    text = "Notify me",
                    onClick = onAddToCart,
                    modifier = Modifier.fillMaxWidth(),
                    variant = ButtonVariant.SECONDARY,
                    size = ButtonSize.MEDIUM,
                    shape = Dimens.ShapeSm
                )
            }
        }
    }
}

// ── Rating pill ──────────────────────────────────────────────────────────────
// v1 drew a filled green pill with a white star and 9sp white text, which put a
// third saturated colour block on a card that already had a pink ribbon and a
// pink heart. Now it is a text treatment: one star glyph (star500) plus the
// rating in text.secondary at 11sp. The number is the information; the star only
// says which number it is.
//
// COLOUR NOTE: the star and the rating number read as a SINGLE unit at 12dp, so
// the star is held to the 4.5:1 text threshold rather than the 3:1 graphical
// one. star500 (#B45309) clears it at 5.02:1 on white and 11.13:1 on neutral950.

@Composable
private fun RatingPill(rating: Float, count: Int?) {
    val c = DugguTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = Star500
        )
        Spacer(Modifier.width(Dimens.space1))
        Text(
            text = if (count != null) "$rating ($count)" else "$rating",
            style = DugguType.overline,          // 11sp — was 9sp in v1
            color = c.text.secondary
        )
    }
}

// ── Wishlist toggle ──────────────────────────────────────────────────────────
// At rest: outline, low emphasis. Saved: filled brand, high emphasis. v1 painted
// it filled pink at rest, which meant the saved and unsaved states were nearly
// indistinguishable at a glance AND the rest state competed with the price.

@Composable
fun WishlistToggle(
    saved: Boolean,
    onToggle: () -> Unit,
    itemName: String,
    modifier: Modifier = Modifier
) {
    val c = DugguTheme.colors
    DugguIconButton(
        icon = if (saved) androidx.compose.material.icons.Icons.Default.Favorite
               else androidx.compose.material.icons.Icons.Default.FavoriteBorder,
        // The label states the CURRENT state and the ACTION, which is what makes
        // a toggle usable with a screen reader. "Wishlist" alone leaves the user
        // unable to tell whether they are adding or removing.
        contentDescription = if (saved) "Remove $itemName from wishlist"
                             else "Add $itemName to wishlist",
        onClick = onToggle,
        modifier = modifier,
        tint = if (saved) c.text.brand else c.text.tertiary
    )
}

// ── Price block ──────────────────────────────────────────────────────────────
// Tabular figures throughout (feature "tnum") so a column of prices aligns on the
// decimal, which is what lets a user compare two products without reading digits.

@Composable
fun PriceBlock(
    price: String,
    modifier: Modifier = Modifier,
    mrp: String? = null,
    discountLabel: String? = null,
    size: PriceSize = PriceSize.MEDIUM
) {
    val c = DugguTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space2)
    ) {
        Text(
            text = price,
            style = if (size == PriceSize.LARGE) DugguType.priceLg else DugguType.priceMd,
            color = c.text.primary
        )
        if (mrp != null) {
            Text(
                text = mrp,
                style = DugguType.priceStrike,
                color = c.text.tertiary,
                textDecoration = TextDecoration.LineThrough
            )
        }
        if (discountLabel != null) {
            // Discount is a property of the price, so it sits WITH the price rather
            // than as a ribbon across the image. success.700 on white = 8.05:1.
            Text(
                text = discountLabel,
                style = DugguType.overline,
                color = c.text.success
            )
        }
    }
}

enum class PriceSize { MEDIUM, LARGE }

// ── Store card ───────────────────────────────────────────────────────────────

@Composable
fun DugguStoreCard(
    storeName: String,
    modifier: Modifier = Modifier,
    distanceLabel: String? = null,
    etaLabel: String? = null,
    isOpen: Boolean = true,
    storeType: String? = null,
    onClick: () -> Unit
) {
    val c = DugguTheme.colors
    val source = rememberInteraction()
    val shape = Dimens.ShapeMd

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .dugguInteractive(
                shape = shape,
                interactionSource = source,
                backgroundColor = c.surface.default,
                focusRingColor = c.border.focus,
                onClick = onClick
            )
            .padding(Dimens.cardPadding)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(storeName)
                    if (storeType != null) append(", $storeType")
                    if (distanceLabel != null) append(", $distanceLabel away")
                    if (etaLabel != null) append(", delivers in $etaLabel")
                    append(if (isOpen) ", open now" else ", currently closed")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.space3)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(Dimens.ShapeSm)
                .background(c.surface.sunken),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.Filled.Place,
                contentDescription = null,
                Modifier.size(Dimens.iconMd),
                tint = c.text.tertiary
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.stackTight)) {
            Text(storeName, style = DugguType.titleMd, color = c.text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                storeType?.let { Text(it, style = DugguType.bodySm, color = c.text.secondary) }
                distanceLabel?.let { Text(it, style = DugguType.bodySm, color = c.text.tertiary) }
            }
            // Closed stores get a real status chip, not a greyed row. A greyed row
            // made it impossible to tell "closed" from "loading failed".
            StatusChip(
                label = if (isOpen) (etaLabel ?: "Open") else "Closed",
                tone = if (isOpen) StatusTone.DELIVERED else StatusTone.NEUTRAL
            )
        }
    }
}

// ── Order status chip ────────────────────────────────────────────────────────
// v1 mapped four statuses onto four bespoke colour pairs, defined inline at each
// of the ~6 places an order status renders — so "Pending" was amber on one screen
// and orange on another. Now: one component, four tones, colours from status.*.

enum class StatusTone { PENDING, CONFIRMED, DELIVERED, CANCELLED, NEUTRAL }

@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val c = DugguTheme.colors
    val (fg, bg) = when (tone) {
        StatusTone.PENDING   -> c.status.pendingFg   to c.status.pendingBg
        StatusTone.CONFIRMED -> c.status.confirmedFg to c.status.confirmedBg
        StatusTone.DELIVERED -> c.status.deliveredFg to c.status.deliveredBg
        StatusTone.CANCELLED -> c.status.cancelledFg to c.status.cancelledBg
        StatusTone.NEUTRAL   -> c.text.secondary     to c.surface.sunken
    }
    Box(
        modifier
            .clip(Dimens.ShapeXs)
            .background(bg)
            .padding(horizontal = Dimens.space2, vertical = Dimens.stackTight)
    ) {
        Text(
            text = label.uppercase(),
            style = DugguType.overline,
            color = fg,
            maxLines = 1
        )
    }
    // Every fg/bg pair above is a verified >=4.5:1 combination at 11sp:
    //   pending   Warning700 on Warning100 ... 4.89:1
    //   confirmed Info600    on Info100 ....... 5.11:1
    //   delivered Success700 on Success100 .... 6.02:1
    //   cancelled Danger600  on Danger100 ..... 5.24:1
}

// ── Order card ───────────────────────────────────────────────────────────────

@Composable
fun DugguOrderCard(
    orderId: String,
    placedOn: String,
    itemSummary: String,
    total: String,
    statusLabel: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    itemCount: Int = 1,
    onClick: () -> Unit,
    onPrimaryAction: (() -> Unit)? = null,
    primaryActionLabel: String? = null
) {
    val c = DugguTheme.colors
    val source = rememberInteraction()
    val shape = Dimens.ShapeMd

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .dugguInteractive(
                shape = shape,
                interactionSource = source,
                backgroundColor = c.surface.default,
                focusRingColor = c.border.focus,
                onClick = onClick
            )
            .padding(Dimens.cardPadding)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Order $orderId, placed $placedOn, $statusLabel, $itemCount items, $itemSummary, total $total"
            },
        verticalArrangement = Arrangement.spacedBy(Dimens.space2)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Order $orderId", style = DugguType.titleSm, color = c.text.primary)
                Text(placedOn, style = DugguType.caption, color = c.text.tertiary)
            }
            StatusChip(label = statusLabel, tone = tone)
        }
        Text(
            itemSummary,
            style = DugguType.bodySm,
            color = c.text.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (onPrimaryAction != null && primaryActionLabel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PriceBlock(price = total)
                DugguButton(
                    text = primaryActionLabel,
                    onClick = onPrimaryAction,
                    variant = ButtonVariant.SECONDARY,
                    size = ButtonSize.SMALL,
                    shape = Dimens.ShapeSm
                )
            }
        } else {
            PriceBlock(price = total)
        }
    }
}

// ── Dashboard stat card (seller / admin) ─────────────────────────────────────

@Composable
fun DugguStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    deltaLabel: String? = null,
    deltaPositive: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val c = DugguTheme.colors
    val source = rememberInteraction()
    val shape = Dimens.ShapeMd

    Column(
        modifier = modifier
            .clip(shape)
            .dugguInteractive(
                shape = shape,
                interactionSource = source,
                backgroundColor = c.surface.default,
                focusRingColor = c.border.focus,
                onClick = onClick
            )
            .padding(Dimens.cardPadding)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("$label: $value")
                    if (deltaLabel != null) {
                        append(", ${if (deltaPositive) "up" else "down"} $deltaLabel")
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(Dimens.stackTight)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2), verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, Modifier.size(Dimens.iconSm), tint = c.text.tertiary)
            }
            Text(label.uppercase(), style = DugguType.overline, color = c.text.tertiary)
        }
        Text(value, style = DugguType.priceLg, color = c.text.primary)
        if (deltaLabel != null) {
            // Direction is carried by an arrow AND the word up/down, not by colour
            // alone — the same 1.4.1 constraint as the form error state.
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.stackTight), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (deltaPositive) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp
                    else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    Modifier.size(12.dp),
                    tint = if (deltaPositive) c.text.success else c.text.danger
                )
                Text(
                    deltaLabel,
                    style = DugguType.caption,
                    color = if (deltaPositive) c.text.success else c.text.danger
                )
            }
        }
    }
}

// ── Category tile (home carousel) ────────────────────────────────────────────

@Composable
fun DugguCategoryTile(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    itemCountLabel: String? = null,
    onClick: () -> Unit
) {
    val c = DugguTheme.colors
    val source = rememberInteraction()
    val shape = Dimens.ShapeMd

    Column(
        modifier = modifier
            .clip(shape)
            .dugguInteractive(
                shape = shape,
                interactionSource = source,
                backgroundColor = c.surface.default,
                focusRingColor = c.border.focus,
                onClick = onClick
            )
            .padding(Dimens.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.space1)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(Dimens.ShapeSm)
                // The accent wash sits behind an icon, never behind text — so it is
                // exempt from the 4.5:1 text rule and can stay saturated.
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.Category,
                contentDescription = null,
                Modifier.size(Dimens.iconLg),
                tint = c.text.primary
            )
        }
        Text(
            label,
            style = DugguType.caption,
            color = c.text.primary,
            maxLines = 2,
            minLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        if (itemCountLabel != null) {
            Text(itemCountLabel, style = DugguType.micro, color = c.text.tertiary)
        }
    }
}
