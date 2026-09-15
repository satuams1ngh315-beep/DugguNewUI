package com.duggustore.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.duggustore.app.data.model.Category
import com.duggustore.app.data.model.Product
import com.duggustore.app.R
import com.duggustore.app.ui.theme.*

/** Premium wordmark — Deep Teal + Lime, 2026 style. */
@Composable
fun StoreWordmark(first: String = "Duggu", second: String = "Store", size: Int = 22) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            first,
            fontSize = size.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PrimaryTeal
        )
        Text(
            second,
            fontSize = size.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AccentLime
        )
    }
}

/**
 * Premium location strip on the gradient header.
 * Pin icon, "Deliver to", address, and chevron on one line.
 */
@Composable
fun LocationBar(
    city: String,
    address: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .dugguClickableFlat { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Deliver to",
                fontSize = 12.sp,
                color = TextPrimary.copy(alpha = 0.65f),
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = address,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun StoreWordmarkBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = SurfaceWhite
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            StoreWordmark(size = 13)
        }
    }
}

/**
 * Myntra-style search: a soft rounded pill in a light grey wash — no border,
 * no shadow — with the magnifier, mic and camera in ink grey and a pink
 * cursor while typing.
 */
@Composable
fun StoreSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search Anything...",
    onMicClick: (() -> Unit)? = null,
    onPhotoSearchClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = SurfaceMuted
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                BasicSearchField(query, onQueryChange, placeholder, onFocusChange)
            }
            if (onMicClick != null || onPhotoSearchClick != null) {
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(BorderGray)
                )
            }
            if (onMicClick != null) {
                IconButton(onClick = onMicClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Mic,
                        stringResource(R.string.home_voice_search),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onPhotoSearchClick != null) {
                IconButton(onClick = onPhotoSearchClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        stringResource(R.string.home_image_search),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BasicSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onFocusChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextPrimary),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentLime),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChange(it.isFocused) },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = TextLight)
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun SearchHistoryPanel(
    terms: List<String>,
    onTermClick: (String) -> Unit,
    onRemoveTerm: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (terms.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_recent_title),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.search_recent_clear),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .dugguClickable { onClearAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            terms.forEach { term ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dugguClickable { onTermClick(term) }
                        .padding(start = 14.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = TextLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = term,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .dugguClickable { onRemoveTerm(term) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_recent_remove),
                            tint = TextLight,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text(
                    "See All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentLime
                )
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = AccentLime,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryTile(
    category: Category,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(104.dp)
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.55f))
            .dugguClickable { onClick() }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = iconForCategory(category.name),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(34.dp)
        )
        Text(
            text = category.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun iconForCategory(name: String): ImageVector = when {
    name.contains("groc", true) -> Icons.Default.LocalGroceryStore
    name.contains("veg", true) -> Icons.Default.Eco
    name.contains("fruit", true) -> Icons.Default.ShoppingBasket
    name.contains("snack", true) -> Icons.Default.Cookie
    name.contains("choc", true) -> Icons.Default.Cake
    name.contains("bread", true) || name.contains("baker", true) -> Icons.Default.BakeryDining
    name.contains("shampoo", true) || name.contains("beauty", true) -> Icons.Default.Spa
    name.contains("clean", true) -> Icons.Default.CleaningServices
    name.contains("baby", true) -> Icons.Default.ChildCare
    name.contains("drink", true) || name.contains("cold", true) -> Icons.Default.LocalDrink
    name.contains("meat", true) -> Icons.Default.SetMeal
    name.contains("dairy", true) || name.contains("milk", true) -> Icons.Default.LocalCafe
    name.contains("frozen", true) -> Icons.Default.AcUnit
    name.contains("fashion", true) || name.contains("cloth", true) -> Icons.Default.Checkroom
    name.contains("appliance", true) || name.contains("electr", true) -> Icons.Default.Kitchen
    name.contains("furni", true) -> Icons.Default.Chair
    else -> Icons.Default.Category
}

internal fun feedImageRequest(
    context: android.content.Context,
    url: String,
    sizePx: Int = 512
): ImageRequest = ImageRequest.Builder(context)
    .data(url)
    .size(sizePx)
    .crossfade(false)
    .allowHardware(true)
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    .build()

@Composable
fun FeedProductImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    if (url.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, null, tint = TextLight, modifier = Modifier.size(44.dp))
        }
        return
    }
    val context = LocalContext.current
    AsyncImage(
        model = remember(url) { feedImageRequest(context, url) },
        contentDescription = contentDescription,
        modifier = modifier.fillMaxSize(),
        contentScale = contentScale
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductImageCarousel(
    images: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    imageModifier: Modifier = Modifier
) {
    if (images.size <= 1) {
        Box(modifier = modifier) {
            val url = images.firstOrNull()
            if (url != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(url) { feedImageRequest(context, url, sizePx = 1080) },
                    contentDescription = contentDescription,
                    modifier = imageModifier.fillMaxSize(),
                    contentScale = contentScale
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, null, tint = TextLight, modifier = Modifier.size(44.dp))
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { images.size })
    val context = LocalContext.current

    Box(modifier = modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val url = images[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .gpuPagerScale(pagerState, page, minScale = 0.92f, minAlpha = 0.85f)
            ) {
                AsyncImage(
                    model = remember(url) { feedImageRequest(context, url, sizePx = 1080) },
                    contentDescription = contentDescription,
                    modifier = imageModifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(images.size) { index ->
                Box(
                    modifier = Modifier.width(15.dp).height(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .gpuPagerDot(pagerState, index)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

/**
 * Premium product card with soft shadow, rounded corners,
 * discount badge, veg mark, and elegant typography.
 */
@Composable
fun StoreProductCard(
    product: Product,
    quantityInCart: Int,
    isFavorite: Boolean,
    onAdd: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .gpuClickableScale(onClick, shape = RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        shadowElevation = 1.dp,
        border = BorderStroke(0.5.dp, DividerGray)
    ) {
        Column {
            // Product image area with rounded top corners
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(SurfaceWhite)
            ) {
                val outOfStock = product.stock <= 0
                FeedProductImage(
                    url = product.images().firstOrNull(),
                    contentDescription = product.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .then(if (outOfStock) Modifier.alpha(0.35f) else Modifier)
                )

                if (outOfStock) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).rotate(-8f),
                        shape = RoundedCornerShape(6.dp),
                        color = TextPrimary.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = "OUT OF STOCK",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Discount badge top-left (premium design)
                if (product.hasDiscount()) {
                    DiscountBadge(
                        percent = discountPercent(product),
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }

                // Veg/Non-veg mark top-right
                product.isVeg?.let { isVeg ->
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceWhite,
                        shadowElevation = 1.dp
                    ) {
                        VegNonVegMark(isVeg = isVeg, modifier = Modifier.padding(3.dp))
                    }
                }
            }

            // Text + price + ADD button area
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                Text(
                    text = product.name,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = product.unit,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = TextSecondary,
                    maxLines = 1
                )

                Spacer(Modifier.height(8.dp))

                // Price + ADD/Stepper row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "₹${trimAmount(product.effectivePrice())}",
                            fontSize = 14.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (product.hasDiscount()) {
                            Text(
                                text = "₹${trimAmount(product.price)}",
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = TextLight,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                    }

                    when {
                        product.stock <= 0 -> Surface(
                            modifier = Modifier.width(68.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceMuted
                        ) {
                            Text(
                                "Out",
                                modifier = Modifier.padding(vertical = 7.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextLight,
                                textAlign = TextAlign.Center
                            )
                        }
                        quantityInCart > 0 -> PremiumStepper(quantityInCart, onDecrease, onIncrease)
                        else -> PremiumAddButton(onAdd)
                    }
                }
            }
        }
    }
}

/** Premium green "ADD" button with rounded corners. */
@Composable
private fun PremiumAddButton(onAdd: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .dugguClickable { onAdd() },
        shape = RoundedCornerShape(10.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.5.dp, AccentLime)
    ) {
        Text(
            text = "ADD",
            modifier = Modifier.padding(vertical = 7.dp),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AccentLime,
            textAlign = TextAlign.Center
        )
    }
}

/** Premium lime quantity stepper with smooth + and - buttons. */
@Composable
private fun PremiumStepper(quantity: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AccentLime),
        color = AccentLime
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 30.dp)
                    .dugguClickable { onDecrease() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Remove,
                    "Decrease",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = "$quantity",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 30.dp)
                    .dugguClickable { onIncrease() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    "Increase",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun QuantityStepperRow(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StepperSquare(Icons.Default.Remove, AccentLime, "Decrease", onDecrease)
        Text(
            text = "$quantity",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = AccentLime
        )
        StepperSquare(Icons.Default.Add, AccentLime, "Increase", onIncrease)
    }
}

@Composable
private fun StepperSquare(icon: ImageVector, color: Color, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(color)
            .dugguClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Myntra-style discount badge — solid pink pill with white bold text. */
@Composable
fun DiscountBadge(percent: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(6.dp),
        shape = RoundedCornerShape(6.dp),
        color = MyntraPink,
        shadowElevation = 1.dp
    ) {
        Text(
            text = "$percent% OFF",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Legacy alias kept for compatibility. */
@Composable
fun DiscountRibbon(percent: Int, modifier: Modifier = Modifier) {
    DiscountBadge(percent, modifier)
}

fun discountPercent(product: Product): Int {
    if (!product.hasDiscount() || product.price <= 0.0) return 0
    return (((product.price - product.effectivePrice()) / product.price) * 100).toInt()
}

fun trimAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)

fun estimatedDeliveryWindow(): String = "10 mins"

@Composable
fun DeliveryEtaBanner(modifier: Modifier = Modifier, etaText: String = estimatedDeliveryWindow()) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AccentLimeSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = AccentLimeDark, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Delivery in $etaText",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentLimeDark
            )
        }
    }
}
