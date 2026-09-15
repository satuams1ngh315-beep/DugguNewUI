package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duggustore.app.R
import com.duggustore.app.data.model.Category
import com.duggustore.app.data.model.Product
import com.duggustore.app.ui.theme.DividerGray
import com.duggustore.app.ui.theme.MyntraPink
import com.duggustore.app.ui.theme.MyntraPinkSurface
import com.duggustore.app.ui.theme.SurfaceMuted
import com.duggustore.app.ui.theme.SurfaceWhite
import com.duggustore.app.ui.theme.TextLight
import com.duggustore.app.ui.theme.TextPrimary
import com.duggustore.app.ui.theme.TextSecondary
import kotlin.math.floor

/**
 * How many products the sheet previews before handing over to the full
 * category view. Small enough that the sheet lands on a screenful rather
 * than a scroll, which is the whole point of it being quicker than the tab.
 */
private const val QUICK_SHOP_PREVIEW = 6

/**
 * A one-tap shortening of the products in a category.
 *
 * Every option here is derived from the products themselves — nothing is
 * seeded, and nothing is offered that would not actually change the list.
 * `categories` has no sub-categories to group by (it is a flat table, and
 * the app never invents a hierarchy the data does not have), so the
 * shortcuts the sheet can honestly offer are the axes the catalogue
 * genuinely carries: a running offer, the middle of the price range, and
 * the veg mark.
 */
enum class QuickFilter { ALL, OFFERS, UNDER_PRICE, VEG }

/** A [QuickFilter] plus whatever the option needs to be applied — only the price cut uses it. */
data class QuickFilterOption(
    val filter: QuickFilter,
    val priceCeiling: Double = 0.0
)

/**
 * The options worth showing for [products], in the order a shopper reaches
 * for them: everything, then the two that surface a deal or a budget, then
 * the dietary mark.
 *
 * Offers, the price cut and the veg mark are each included only when they
 * would actually remove something, so a category with nothing on discount
 * never shows an "Offers" chip that does nothing.
 */
fun quickShopOptions(products: List<Product>): List<QuickFilterOption> {
    val options = mutableListOf(QuickFilterOption(QuickFilter.ALL))

    if (products.any { it.hasDiscount() } && products.any { !it.hasDiscount() }) {
        options += QuickFilterOption(QuickFilter.OFFERS)
    }

    priceCut(products)?.let { options += QuickFilterOption(QuickFilter.UNDER_PRICE, it) }

    if (products.any { it.isVeg == true } && products.any { it.isVeg != true }) {
        options += QuickFilterOption(QuickFilter.VEG)
    }

    return options
}

fun applyQuickFilter(products: List<Product>, option: QuickFilterOption): List<Product> =
    when (option.filter) {
        QuickFilter.ALL -> products
        QuickFilter.OFFERS -> products.filter { it.hasDiscount() }
        QuickFilter.UNDER_PRICE -> products.filter { it.effectivePrice() < option.priceCeiling }
        QuickFilter.VEG -> products.filter { it.isVeg == true }
    }

/**
 * The middle of the category's price range, rounded to a number a shopper
 * reads at a glance — tens below ₹200, fifties above. A median rather than
 * a mean on purpose: one ₹2,000 item in a shelf of ₹40 ones should not drag
 * the cut-off up past everything that is actually on it.
 *
 * Null when no cut-off would split the list (a single product, or every
 * product at the same price), because a chip that filters nothing is worse
 * than no chip.
 */
private fun priceCut(products: List<Product>): Double? {
    if (products.size < 2) return null

    val prices = products.map { it.effectivePrice() }.sorted()
    val median = prices[prices.size / 2]
    val rounded = if (median < 200) floor(median / 10) * 10 else floor(median / 50) * 50

    val splits = rounded > 0 &&
        products.any { it.effectivePrice() < rounded } &&
        products.any { it.effectivePrice() >= rounded }

    return if (splits) rounded else null
}

/**
 * One category as a place to buy from, not a filter to apply.
 *
 * The home tiles used to re-filter the feed underneath them, which is a
 * two-step move: tap, then scroll to find the shelf you were sent to. This
 * sheet answers the tap where it happened — the category's own products,
 * add-to-bag right there, and one honest way out ([onViewAll]) for the
 * shopper who does want the full list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickShopSheet(
    category: Category,
    products: List<Product>,
    cartQuantities: Map<String, Int>,
    favoriteIds: Set<String>,
    onAddToCart: (Product) -> Unit,
    onIncrease: (Product) -> Unit,
    onDecrease: (Product) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    onProductClick: (Product) -> Unit,
    onViewAll: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Held as a local, like the home tiles do, so the null check below still
    // narrows inside the remember{} lambda that builds the image request.
    val iconUrl = category.iconUrl

    val options = remember(products) { quickShopOptions(products) }

    // Reset with the category, so opening a second one never arrives already
    // filtered by a choice made about the first.
    var selected by remember(category.id) { mutableStateOf(QuickFilter.ALL) }

    // The list can be replaced under a live selection (a filter chip only
    // exists while it applies), so fall back rather than rendering nothing.
    val active = options.firstOrNull { it.filter == selected } ?: options.first()
    val visible = remember(products, active) { applyQuickFilter(products, active) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceMuted)
                        .border(0.5.dp, DividerGray, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = remember(iconUrl) {
                                feedImageRequest(context, iconUrl, sizePx = 256)
                            },
                            contentDescription = category.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                        )
                    } else {
                        Icon(
                            imageVector = iconForCategory(category.name),
                            contentDescription = category.name,
                            tint = MyntraPink,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isLoading && products.isEmpty()) {
                            stringResource(R.string.quick_shop_loading)
                        } else {
                            stringResource(R.string.quick_shop_count, products.size)
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            if (options.size > 1) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        QuickFilterChip(
                            label = quickFilterLabel(option),
                            selected = option.filter == active.filter,
                            onClick = { selected = option.filter }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                isLoading && products.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MyntraPink, strokeWidth = 2.5.dp)
                }

                products.isEmpty() -> QuickShopMessage(
                    message = stringResource(R.string.quick_shop_empty)
                )

                visible.isEmpty() -> QuickShopMessage(
                    message = stringResource(R.string.quick_shop_none_match)
                )

                else -> Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    visible.take(QUICK_SHOP_PREVIEW).chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { product ->
                                StoreProductCard(
                                    product = product,
                                    quantityInCart = cartQuantities[product.id] ?: 0,
                                    isFavorite = favoriteIds.contains(product.id),
                                    onAdd = { onAddToCart(product) },
                                    onIncrease = { onIncrease(product) },
                                    onDecrease = { onDecrease(product) },
                                    onToggleFavorite = { onToggleFavorite(product) },
                                    onClick = { onProductClick(product) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Keeps a lone last card the width of a column in
                            // a full row, instead of stretching it across both.
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (visible.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MyntraPinkSurface)
                        .border(1.dp, MyntraPink, RoundedCornerShape(14.dp))
                        .dugguClickableFlat(onViewAll)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.quick_shop_view_all, products.size),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MyntraPink
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MyntraPink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The same pink-when-active, hairline-when-not language as the home tiles. */
@Composable
private fun QuickFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MyntraPinkSurface else SurfaceWhite)
            .border(1.dp, if (selected) MyntraPink else DividerGray, shape)
            .dugguClickableFlat(onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MyntraPink else TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun quickFilterLabel(option: QuickFilterOption): String = when (option.filter) {
    QuickFilter.ALL -> stringResource(R.string.quick_filter_all)
    QuickFilter.OFFERS -> stringResource(R.string.quick_filter_offers)
    QuickFilter.UNDER_PRICE ->
        stringResource(R.string.quick_filter_under, trimAmount(option.priceCeiling))
    QuickFilter.VEG -> stringResource(R.string.quick_filter_veg)
}

@Composable
private fun QuickShopMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = TextLight,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

