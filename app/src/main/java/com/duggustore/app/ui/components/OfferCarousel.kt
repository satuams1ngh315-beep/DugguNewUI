package com.duggustore.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.duggustore.app.data.model.Coupon
import com.duggustore.app.data.model.Product
import com.duggustore.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How long each banner holds before the carousel moves on. */
private const val AUTO_ADVANCE_MS = 4000L

/**
 * How many percentage points a product's own discount may differ from a
 * coupon's headline figure and still count as that coupon's product — just
 * enough slack to absorb [discountPercent]'s truncation to an Int, not loose
 * enough to let one steeply-discounted item satisfy every coupon on the rail.
 */
private const val MATCH_TOLERANCE = 2

/**
 * The offers strip on home. Not every card on it is a discount coupon any
 * more — [banners] can mix in anything built by [buildDiscountBanners] or
 * assembled by the caller (a new-arrival spotlight, a wallet reminder, a
 * referral invite…), and this only knows how to page through and render
 * them. Cards take their tint from whoever built them, so a mixed rail
 * still reads as one family rather than one coupon look plus odd ones out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfferCarousel(
    banners: List<PromoBanner>,
    modifier: Modifier = Modifier,
    // Sampled inside the auto-advance coroutine, never during composition.
    // Reading isScrollInProgress in the home item lambda used to recompose
    // this pager (and, through it, the feed) at the start and end of every
    // fling — which is exactly when the scroll needed to stay cheap.
    feedListState: LazyListState? = null,
    /**
     * Supply this from a scope that outlives the rail. The carousel sits in
     * the feed's LazyColumn, so scrolling it off the top disposes it and
     * anything remembered in here dies with it — which is what used to send
     * the rail back to card one, restart the auto-advance and re-request the
     * artwork every time it came back into view. Hoisted, the page survives
     * and the card is still the one the customer left on.
     */
    hoistedPagerState: PagerState? = null
) {
    if (banners.isEmpty()) return

    val bannerCount = remember(banners) { banners.size }
    // The fallback keeps this usable on its own; the branch is stable for
    // any given call site, so it never swaps one state for the other
    // mid-composition.
    val pagerState = hoistedPagerState ?: rememberPagerState(pageCount = { bannerCount })

    // Only advances while the user is not touching it — a card that slides away
    // mid-tap is worse than one that waits — and never with a single card,
    // where it would animate to the page it is already on.
    //
    // feedListState.isScrollInProgress is read here, in the coroutine, so a
    // fling on the parent list does not invalidate composition at all.
    LaunchedEffect(pagerState, bannerCount, feedListState) {
        if (bannerCount < 2) return@LaunchedEffect
        // A fling on the home list that starts *during* an auto-advance
        // used to keep the rail animating (and its GPU scale invalidating
        // draw) for the rest of the page-turn — two scroll animations at
        // once. Snap the pager still the moment the parent list moves.
        if (feedListState != null) {
            launch {
                snapshotFlow { feedListState.isScrollInProgress }
                    .collect { scrolling ->
                        if (scrolling && pagerState.isScrollInProgress) {
                            pagerState.scrollToPage(pagerState.currentPage)
                        }
                    }
            }
        }
        while (true) {
            delay(AUTO_ADVANCE_MS)
            val feedBusy = feedListState?.isScrollInProgress == true
            if (!pagerState.isScrollInProgress && !feedBusy) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % bannerCount)
            }
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            // Wider than before, so more of the previous and next card
            // shows at each edge, and a tighter pageSpacing means that
            // extra peek is actual neighbouring card rather than gap.
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            OfferCard(
                banner = banners[page],
                modifier = Modifier.gpuPagerScale(pagerState, page)
            )
        }

        if (banners.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(banners.size) { index ->
                    // 18dp slot so a GPU-scaled pill (6→18) never overlaps
                    // its neighbour — scale happens in the layer, layout
                    // width stays put and does not remeasure the row.
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(18.dp)
                            .height(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .gpuPagerDot(pagerState, index)
                                .clip(CircleShape)
                                .background(banners[index].tint)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Turns the store's active coupons into discount [PromoBanner]s, each paired
 * with at most one product and each product with at most one coupon —
 * closest percentage match wins, greedily, closest first — so a single
 * steeply-discounted item can't end up "featured" on every coupon just
 * because it also clears every other one's lower threshold. A coupon with
 * nothing close enough features no product at all, which is a normal
 * outcome, not a fallback to guess at.
 */
fun buildDiscountBanners(
    coupons: List<Coupon>,
    products: List<Product>,
    onClick: (Coupon) -> Unit
): List<PromoBanner> {
    val candidates = products.filter { it.hasDiscount() }
    val pairs = if (candidates.isEmpty()) emptyList() else coupons.flatMap { coupon ->
        candidates.map { product -> Triple(coupon, product, abs(discountPercent(product) - coupon.discountPercent)) }
    }.filter { it.third <= MATCH_TOLERANCE }.sortedBy { it.third }

    val claimedProducts = mutableSetOf<String>()
    val featuredByCoupon = mutableMapOf<String, Product>()
    for ((coupon, product, _) in pairs) {
        if (featuredByCoupon.containsKey(coupon.id) || product.id in claimedProducts) continue
        featuredByCoupon[coupon.id] = product
        claimedProducts += product.id
    }

    return coupons.mapIndexed { index, coupon ->
        PromoBanner(
            id = "coupon:${coupon.id}",
            tint = CategoryColors[index.mod(CategoryColors.size)],
            eyebrowIcon = Icons.Default.LocalOffer,
            eyebrow = coupon.expiryLabel.ifBlank { "Limited time" },
            headline = coupon.title.ifBlank { "${coupon.discountPercent}% OFF" },
            subtitle = coupon.description,
            chipLabel = "CODE  ${coupon.code}",
            featuredProduct = featuredByCoupon[coupon.id],
            onClick = { onClick(coupon) }
        )
    }
}

/**
 * Four discs for one card's cluster, all in the same colour family as the
 * card's own tint — lighter/darker/more-or-less-saturated shades of it,
 * plus two slight hue shifts either side — rather than unrelated palette
 * colours that read as a random, clashing collage sitting on top of the
 * card's own colour.
 */
private fun discColorsFor(tint: Color): List<Color> {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(tint.toArgb(), hsv)
    val (hue, sat, value) = hsv

    fun shade(hueShift: Float, satMul: Float, valueMul: Float): Color {
        val shifted = floatArrayOf(
            (hue + hueShift + 360f) % 360f,
            (sat * satMul).coerceIn(0.2f, 1f),
            (value * valueMul).coerceIn(0.35f, 1f)
        )
        return Color(AndroidColor.HSVToColor(shifted))
    }

    return listOf(
        shade(hueShift = 0f, satMul = 0.65f, valueMul = 1.2f),
        shade(hueShift = -16f, satMul = 1.15f, valueMul = 0.82f),
        shade(hueShift = 14f, satMul = 0.9f, valueMul = 1.05f),
        shade(hueShift = 0f, satMul = 1.35f, valueMul = 0.65f)
    )
}

/** One flat, plain disc in the decorative cluster — no rim, no highlight. */
@Composable
private fun DecorativeDisc(size: Dp, offsetX: Dp, offsetY: Dp, color: Color, alpha: Float) {
    Box(
        modifier = Modifier
            .size(size)
            .offset(x = offsetX, y = offsetY)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun OfferCard(banner: PromoBanner, modifier: Modifier = Modifier) {
    // Use server-side cutout image if available, otherwise fall back to first regular image
    val featuredProduct = banner.featuredProduct
    val featuredImageUrl = featuredProduct?.cutoutImage()

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Sized to the card's own content (icon+label row, title,
            // two-line description, code chip, plus its padding) rather than
            // matched to the product card's height — that left a visibly
            // empty band under the text instead of a snug banner.
            .height(186.dp)
            .clip(RoundedCornerShape(20.dp))
            // A light wash of the card's colour rather than the colour itself:
            // the reference banner is a pale tint with dark type on it, and a
            // fully saturated panel reads far heavier than the rest of the page.
            .background(banner.tint.copy(alpha = 0.14f))
            .clickable { banner.onClick() }
    ) {
        if (featuredProduct != null && featuredImageUrl != null) {
            // The matched product's photo, background cut out on server side
            // for better performance — sitting under the two decorative discs,
            // not filling the whole card.
            CutoutProductImage(
                imageUrl = featuredImageUrl,
                contentDescription = featuredProduct.name,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
                    .size(128.dp)
            )
        }

        // A cluster of flat discs, in more than one colour so it reads as a
        // little collage rather than one flat tint — and never in the
        // card's own tint, so no disc just blends into the background it's
        // sitting on. Each one is placed with enough of a gap from the
        // others that the cluster reads as spread out rather than one blob
        // in a single corner. The largest anchors the top-right corner
        // (bleeding off both edges); the rest step diagonally down and
        // left across the photo. Drawn after the photo so they sit on top
        // of it, same for every card regardless of whether a product is
        // featured.
        val discColors = remember(banner.tint) { discColorsFor(banner.tint) }
        DecorativeDisc(size = 150.dp, offsetX = 235.dp, offsetY = (-60).dp, color = discColors[0], alpha = 0.16f)
        DecorativeDisc(size = 95.dp, offsetX = 155.dp, offsetY = 30.dp, color = discColors[1], alpha = 0.18f)
        DecorativeDisc(size = 75.dp, offsetX = 245.dp, offsetY = 100.dp, color = discColors[2], alpha = 0.20f)
        DecorativeDisc(size = 55.dp, offsetX = 185.dp, offsetY = 140.dp, color = discColors[3], alpha = 0.18f)

        banner.cornerTag?.let { tag ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(7.dp),
                color = banner.tint
            ) {
                Text(
                    text = tag,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                // Narrowed only when a featured product is actually taking
                // up the right side — otherwise the text keeps the full
                // width it always had.
                .fillMaxWidth(if (featuredImageUrl != null) 0.62f else 1f)
                .fillMaxHeight()
                .padding(18.dp),
            // A corner tag already occupies the top-left, so the text sits
            // at the bottom instead of centred, rather than crowding it.
            verticalArrangement = if (banner.cornerTag != null) Arrangement.Bottom else Arrangement.Center
        ) {
            if (banner.eyebrow.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        banner.eyebrowIcon,
                        contentDescription = null,
                        tint = banner.tint,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = banner.eyebrow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = banner.tint
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = banner.headline,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = banner.subtitle,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 2,
                lineHeight = 16.sp,
                overflow = TextOverflow.Ellipsis
            )

            banner.chipLabel?.let { label ->
                Spacer(Modifier.height(10.dp))
                // The point of the card, so it is set apart rather than
                // buried in the description.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(banner.tint)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * A product photo with background removed on the server side.
 * This is much more performant than on-device ML Kit processing.
 * Falls back to the original image if cutout is not available.
 */
@Composable
private fun CutoutProductImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    // Coil caches decoded bitmaps in memory + disk automatically.
    // memoryCacheKey + diskCacheKey ensure the same cutout image
    // is reused across carousel pages instead of re-decoded on
    // every frame — this is the main cause of jank during scroll.
    val context = LocalContext.current
    AsyncImage(
        model = remember(imageUrl) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .size(384)
                .crossfade(false)
                .allowHardware(true)
                .memoryCacheKey(imageUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        },
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
