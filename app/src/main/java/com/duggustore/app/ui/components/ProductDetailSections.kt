package com.duggustore.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duggustore.app.R
import com.duggustore.app.data.model.Coupon
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.Review
import com.duggustore.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The pieces a product page is made of, kept out of ProductDetailScreen so
 * that file reads as the page's composition rather than as a dozen screens'
 * worth of layout.
 *
 * Everything here is fed real data — offers are the store's actual coupons,
 * the specs come off the product row, ratings are the rows customers wrote —
 * so no block on the page states something the app cannot back up.
 */

/** Shared section heading: bold ink title, optional muted caption on the right. */
@Composable
fun PdpSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (caption != null) {
            Text(text = caption, fontSize = 11.sp, color = TextLight)
        }
    }
}

/** The thick grey band Myntra puts between the page's white blocks. */
@Composable
fun PdpBlockDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Background)
    )
}

/**
 * Product page app bar: white, back on the left, share and wishlist on the
 * right, hairline underneath.
 */
@Composable
fun PdpTopBar(
    isFavorite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = SurfaceWhite, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.pd_share),
                        tint = TextPrimary
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) stringResource(R.string.pd_remove_favourite)
                                             else stringResource(R.string.pd_add_favourite),
                        tint = if (isFavorite) MyntraPink else TextPrimary
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerGray)
            )
        }
    }
}

/**
 * Name, pack size, rating summary and the price block — struck-through MRP,
 * the pink discount and the "inclusive of taxes" line marketplaces show
 * under a price.
 */
@Composable
fun PdpTitleBlock(
    product: Product,
    averageRating: Double?,
    ratingCount: Int,
    reviewCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        product.isVeg?.let { isVeg ->
            VegNonVegMark(isVeg = isVeg, boxSize = 15.dp)
            Spacer(Modifier.height(6.dp))
        }

        Text(
            text = product.name,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        if (product.unit.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(text = product.unit, fontSize = 13.sp, color = TextSecondary)
        }

        if (averageRating != null && ratingCount > 0) {
            Spacer(Modifier.height(9.dp))
            RatingChipRow(
                averageRating = averageRating,
                ratingCount = ratingCount,
                reviewCount = reviewCount
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "₹${trimAmount(product.effectivePrice())}",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (product.hasDiscount()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "₹${trimAmount(product.price)}",
                    modifier = Modifier.padding(bottom = 2.dp),
                    fontSize = 15.sp,
                    color = TextLight,
                    textDecoration = TextDecoration.LineThrough
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${discountPercent(product)}% OFF",
                    modifier = Modifier.padding(bottom = 2.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MyntraPink
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.pd_inclusive_taxes),
            fontSize = 11.sp,
            color = SuccessGreen,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Green rating pill plus how many ratings and written reviews back it. */
@Composable
private fun RatingChipRow(
    averageRating: Double,
    ratingCount: Int,
    reviewCount: Int
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(6.dp), color = RatingGreen) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "%.1f".format(averageRating),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (ratingCount == 1) stringResource(R.string.pd_one_rating)
                   else stringResource(R.string.pd_rating_count, ratingCount),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
        if (reviewCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text("•", fontSize = 12.sp, color = TextLight)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (reviewCount == 1) stringResource(R.string.pd_one_review)
                       else stringResource(R.string.pd_review_count, reviewCount),
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

/**
 * "Best offers" — the store's real coupons as tappable cards whose code goes
 * straight to the clipboard. A coupon whose minimum order this item alone
 * already meets is marked as usable, so the rail never advertises a code the
 * customer cannot spend.
 */
@Composable
fun PdpOffersRail(
    offers: List<Coupon>,
    productPrice: Double,
    modifier: Modifier = Modifier
) {
    if (offers.isEmpty()) return
    val clipboard = LocalClipboardManager.current
    var copiedCode by remember { mutableStateOf<String?>(null) }

    PdpBlockDivider()

    Column(modifier = modifier.fillMaxWidth().background(SurfaceWhite)) {
        PdpSectionHeader(
            title = stringResource(R.string.pd_best_offers),
            caption = if (offers.size > 1) stringResource(R.string.pd_swipe_more) else null
        )

        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(offers, key = { it.id.ifBlank { it.code } }) { coupon ->
                val applicable = coupon.minOrderValue <= productPrice
                Surface(
                    modifier = Modifier
                        .width(238.dp)
                        .dugguClickable {
                            clipboard.setText(AnnotatedString(coupon.code))
                            copiedCode = coupon.code
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (applicable) MyntraPinkSurface else SurfaceMuted,
                    border = BorderStroke(0.5.dp, if (applicable) MyntraPinkOutline else DividerGray)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            Icons.Default.LocalOffer,
                            contentDescription = null,
                            tint = if (applicable) MyntraPink else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = coupon.title.ifBlank {
                                    if (coupon.discountPercent > 0)
                                        stringResource(R.string.pd_offer_percent, coupon.discountPercent)
                                    else stringResource(R.string.pd_best_offers)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (coupon.minOrderValue > 0) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.pd_offer_min_order, coupon.minOrderValue),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            if (coupon.maxDiscount > 0) {
                                Text(
                                    text = stringResource(R.string.pd_offer_max_discount, coupon.maxDiscount),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SurfaceWhite,
                                    border = BorderStroke(
                                        1.dp,
                                        if (applicable) MyntraPinkOutline else BorderGray
                                    )
                                ) {
                                    Text(
                                        text = coupon.code,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (applicable) MyntraPink else TextPrimary
                                    )
                                }
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = if (copiedCode == coupon.code)
                                        stringResource(R.string.pd_code_copied)
                                    else stringResource(R.string.pd_tap_to_copy),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (copiedCode == coupon.code) SuccessGreen else TextLight
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One label/value line of the specification table. */
private data class SpecRow(val label: String, val value: String)

/**
 * The specification table. Every row is a fact off the product row rather
 * than boilerplate, and a row is left out entirely when there is nothing
 * true to put in it — an empty "Type" on a cleaning spray would just be
 * noise.
 */
@Composable
fun PdpSpecTable(
    product: Product,
    categoryName: String?,
    modifier: Modifier = Modifier
) {
    // Every string is read here, in composable context, before the list is
    // assembled — `buildList`'s lambda is a plain (if inline) builder, and
    // keeping the resource reads out of it means the rows are a pure function
    // of the product rather than of the composition around them.
    val labelCategory = stringResource(R.string.pd_spec_category)
    val labelPackSize = stringResource(R.string.pd_spec_pack_size)
    val labelType = stringResource(R.string.pd_spec_type)
    val labelAvailability = stringResource(R.string.pd_spec_availability)
    val labelDiscount = stringResource(R.string.pd_spec_discount)
    val labelListedOn = stringResource(R.string.pd_spec_listed_on)
    val valueVeg = stringResource(R.string.pd_type_veg)
    val valueNonVeg = stringResource(R.string.pd_type_non_veg)
    val valueAvailability = if (product.stock > 0) {
        stringResource(R.string.pd_spec_in_stock, product.stock)
    } else {
        stringResource(R.string.pd_spec_out_of_stock)
    }
    val valueDiscount = if (product.hasDiscount()) {
        stringResource(
            R.string.pd_spec_discount_value,
            discountPercent(product),
            trimAmount(product.savingsAmount())
        )
    } else null
    val valueListedOn = formatPdpDate(product.createdAt)

    val rows = buildList {
        if (!categoryName.isNullOrBlank()) add(SpecRow(labelCategory, categoryName))
        if (product.unit.isNotBlank()) add(SpecRow(labelPackSize, product.unit))
        product.isVeg?.let { isVeg ->
            add(SpecRow(labelType, if (isVeg) valueVeg else valueNonVeg))
        }
        add(SpecRow(labelAvailability, valueAvailability))
        if (valueDiscount != null) add(SpecRow(labelDiscount, valueDiscount))
        if (valueListedOn != null) add(SpecRow(labelListedOn, valueListedOn))
    }

    if (rows.isEmpty()) return

    PdpBlockDivider()

    Column(modifier = modifier.fillMaxWidth().background(SurfaceWhite)) {
        PdpSectionHeader(title = stringResource(R.string.pd_product_details))
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Divider(color = DividerGray, modifier = Modifier.padding(vertical = 9.dp))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = row.label,
                        modifier = Modifier.width(118.dp),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = row.value,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/** One icon-led row of the delivery & services panel. */
@Composable
private fun ServiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showDivider: Boolean
) {
    Column {
        if (showDivider) {
            Divider(
                color = DividerGray,
                modifier = Modifier.padding(start = 62.dp, top = 11.dp, bottom = 11.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Delivery window, cash on delivery, the seven-day issue window and payment
 * safety — the promises this app can actually keep, because each one is a
 * feature it already has: order tracking with a live ETA, `payment_method =
 * cod` at checkout, order issues that resolve to a wallet refund, and
 * payments that never leave the app's own Supabase project.
 */
@Composable
fun PdpDeliveryServices(
    etaText: String,
    modifier: Modifier = Modifier
) {
    PdpBlockDivider()

    Column(modifier = modifier.fillMaxWidth().background(SurfaceWhite)) {
        PdpSectionHeader(title = stringResource(R.string.pd_delivery_services))
        ServiceRow(
            icon = Icons.Default.LocalShipping,
            title = stringResource(R.string.pd_delivery_eta, etaText),
            subtitle = stringResource(R.string.pd_delivery_eta_sub),
            showDivider = false
        )
        ServiceRow(
            icon = Icons.Default.Payments,
            title = stringResource(R.string.pd_cod),
            subtitle = stringResource(R.string.pd_cod_sub),
            showDivider = true
        )
        ServiceRow(
            icon = Icons.Default.SwapHoriz,
            title = stringResource(R.string.pd_returns),
            subtitle = stringResource(R.string.pd_returns_sub),
            showDivider = true
        )
        ServiceRow(
            icon = Icons.Default.Shield,
            title = stringResource(R.string.pd_secure_payments),
            subtitle = stringResource(R.string.pd_secure_payments_sub),
            showDivider = true
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** Description plus the short "why this is worth it" bullets. */
@Composable
fun PdpDescription(
    product: Product,
    modifier: Modifier = Modifier
) {
    // Same reasoning as the spec table: resources first, list second.
    val saveText = stringResource(R.string.pd_highlight_save, trimAmount(product.savingsAmount()))
    val lowStockText = stringResource(R.string.pd_highlight_low_stock, product.stock)
    val stockText = stringResource(R.string.pd_highlight_stock, product.stock)
    val vegText = stringResource(R.string.pd_highlight_veg)
    val packText = stringResource(R.string.pd_highlight_pack, product.unit)

    val highlights = buildList {
        if (product.hasDiscount()) add(saveText)
        if (product.stock in 1..5) {
            add(lowStockText)
        } else if (product.stock > 5) {
            add(stockText)
        }
        if (product.isVeg == true) add(vegText)
        if (product.unit.isNotBlank()) add(packText)
    }

    if (product.description.isBlank() && highlights.isEmpty()) return

    PdpBlockDivider()

    Column(modifier = modifier.fillMaxWidth().background(SurfaceWhite)) {
        PdpSectionHeader(title = stringResource(R.string.pd_description))
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (product.description.isNotBlank()) {
                Text(
                    text = product.description,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = TextSecondary
                )
            }
            if (highlights.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.pd_highlights),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                highlights.forEach { highlight ->
                    Row(
                        modifier = Modifier.padding(bottom = 7.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = highlight,
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ratings and reviews, marketplace style: the average as one large figure,
 * how the stars actually split, then the written reviews newest first.
 *
 * Reviewer names are not shown, and that is deliberate rather than an
 * omission — the `profiles` table is readable only by its owner under RLS, so
 * the name is not a thing this screen can know. What it shows instead is what
 * the review row itself carries, so the section never invents an identity.
 */
@Composable
fun PdpRatingsReviews(
    reviews: List<Review>,
    modifier: Modifier = Modifier
) {
    val distribution = remember(reviews) {
        (5 downTo 1).associateWith { star -> reviews.count { it.rating == star } }
    }
    val average = remember(reviews) {
        if (reviews.isEmpty()) null else reviews.sumOf { it.rating } / reviews.size.toDouble()
    }
    val withComments = remember(reviews) { reviews.filter { it.comment.isNotBlank() } }

    PdpBlockDivider()

    Column(modifier = modifier.fillMaxWidth().background(SurfaceWhite)) {
        PdpSectionHeader(
            title = stringResource(R.string.pd_ratings_reviews),
            caption = when {
                reviews.isEmpty() -> null
                reviews.size == 1 -> stringResource(R.string.pd_one_rating)
                else -> stringResource(R.string.pd_rating_count, reviews.size)
            }
        )

        if (average == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) {
                        Icon(
                            Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = TextLight,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.pd_no_reviews),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.pd_no_reviews_sub),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f".format(average),
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Row {
                        repeat(5) { index ->
                            Icon(
                                imageVector = if (index < average.toInt()) Icons.Default.Star
                                              else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = RatingGreen,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (reviews.size == 1) stringResource(R.string.pd_one_rating)
                               else stringResource(R.string.pd_rating_count, reviews.size),
                        fontSize = 11.sp,
                        color = TextLight
                    )
                }

                Spacer(Modifier.width(20.dp))

                // Star-by-star split, each bar scaled against the biggest bucket
                // so a product with three reviews still reads as a shape rather
                // than three invisible slivers.
                val maxCount = (distribution.values.maxOrNull() ?: 0).coerceAtLeast(1)
                Column(modifier = Modifier.weight(1f)) {
                    distribution.forEach { (star, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$star",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.width(10.dp)
                            )
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = TextLight,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SurfaceMuted)
                            ) {
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(count.toFloat() / maxCount)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(RatingGreen)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "$count",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.width(22.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (withComments.isEmpty()) {
                Text(
                    text = stringResource(R.string.pd_no_written_reviews),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(18.dp))
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    withComments.forEach { review -> ReviewCard(review) }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/** One written review: its own star pill, when it was left, and the text. */
@Composable
private fun ReviewCard(review: Review) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = RatingGreen) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${review.rating}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = TextLight,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.pd_verified_purchase),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Spacer(Modifier.weight(1f))
            formatPdpDate(review.createdAt)?.let { date ->
                Text(text = date, fontSize = 11.sp, color = TextLight)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = review.comment,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = TextSecondary
        )
    }
}

/**
 * A horizontal rail of products — "Similar products" from the same category,
 * "More from this store" from the same seller. Both are active, in stock and
 * never the product the page is already showing.
 */
@Composable
fun PdpProductRail(
    title: String,
    products: List<Product>,
    cartQuantities: Map<String, Int>,
    onAdd: (Product) -> Unit,
    onProductClick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) return

    PdpBlockDivider()

    Column(modifier = modifier.fillMaxWidth().background(SurfaceWhite)) {
        PdpSectionHeader(title = title, caption = stringResource(R.string.pd_swipe_more))
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(products, key = { it.id }) { item ->
                PdpRailCard(
                    product = item,
                    quantityInCart = cartQuantities[item.id] ?: 0,
                    onAdd = { onAdd(item) },
                    onClick = { onProductClick(item) }
                )
            }
        }
    }
}

@Composable
private fun PdpRailCard(
    product: Product,
    quantityInCart: Int,
    onAdd: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = product.images().firstOrNull()

    Column(
        modifier = Modifier
            .width(132.dp)
            .dugguClickable(onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNullOrBlank()) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = TextLight,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                AsyncImage(
                    model = remember(imageUrl) { feedImageRequest(context, imageUrl, sizePx = 384) },
                    contentDescription = product.name,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            }
            if (product.hasDiscount()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MyntraPink
                ) {
                    Text(
                        text = "${discountPercent(product)}% OFF",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = product.name,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "₹${trimAmount(product.effectivePrice())}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (product.hasDiscount()) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "₹${trimAmount(product.price)}",
                    fontSize = 11.sp,
                    color = TextLight,
                    textDecoration = TextDecoration.LineThrough
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        if (quantityInCart > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MyntraPinkSurface,
                border = BorderStroke(1.dp, MyntraPinkOutline)
            ) {
                Text(
                    text = stringResource(R.string.pd_in_bag_short, quantityInCart),
                    modifier = Modifier.padding(vertical = 5.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MyntraPink,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .dugguClickable(onAdd),
                shape = RoundedCornerShape(6.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, MyntraPinkOutline)
            ) {
                Text(
                    text = stringResource(R.string.cart_add),
                    modifier = Modifier.padding(vertical = 5.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MyntraPink,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * "12 Aug 2026" from a Supabase timestamp.
 *
 * Hand-parsed on purpose: the app supports API 24 with no core-library
 * desugaring, so java.time's formatters are not available here. Returns null
 * for anything that doesn't match one of the shapes Postgres actually sends,
 * so a caller omits the row rather than printing a raw machine string.
 */
fun formatPdpDate(iso: String): String? {
    if (iso.isBlank()) return null
    val cleaned = iso.substringBefore('.').removeSuffix("Z").trim()
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd")
    patterns.forEach { pattern ->
        try {
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val parsed = parser.parse(cleaned) ?: return@forEach
            return SimpleDateFormat("d MMM yyyy", Locale.US).format(parsed)
        } catch (_: Exception) {
            // Try the next shape.
        }
    }
    return null
}
