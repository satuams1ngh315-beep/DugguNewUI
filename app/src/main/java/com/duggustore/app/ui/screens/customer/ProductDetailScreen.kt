package com.duggustore.app.ui.screens.customer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.data.model.Coupon
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.Review
import com.duggustore.app.data.repository.CategoryRepository
import com.duggustore.app.data.repository.OfferRepository
import com.duggustore.app.data.repository.ProductRepository
import com.duggustore.app.ui.components.PdpDeliveryServices
import com.duggustore.app.ui.components.PdpDescription
import com.duggustore.app.ui.components.PdpOffersRail
import com.duggustore.app.ui.components.PdpProductRail
import com.duggustore.app.ui.components.PdpRatingsReviews
import com.duggustore.app.ui.components.PdpSpecTable
import com.duggustore.app.ui.components.PdpTitleBlock
import com.duggustore.app.ui.components.PdpTopBar
import com.duggustore.app.ui.components.ProductImageCarousel
import com.duggustore.app.ui.components.discountPercent
import com.duggustore.app.ui.components.dugguClickable
import com.duggustore.app.ui.components.estimatedDeliveryWindow
import com.duggustore.app.ui.components.trimAmount
import com.duggustore.app.ui.theme.*

/**
 * The product page.
 *
 * Beyond the product row it is handed (name, price, photos, description) the
 * page loads four more real things for itself: the category it is filed
 * under, the store's live coupons, other in-stock products from the same
 * category, and the seller's other listings. Nothing on the page is
 * decorative — a block with nothing true to show simply does not render, so
 * the page never grows placeholder sections.
 *
 * Reviewer names are the one thing deliberately left out: `profiles` is
 * readable only by its owner under RLS, so a rating is shown as a rating
 * rather than attributed to a name the screen cannot actually fetch.
 */
@Composable
fun ProductDetailScreen(
    product: Product?,
    isFavorite: Boolean,
    reviews: List<Review> = emptyList(),
    onAddToCart: (Product, Int) -> Unit,
    onToggleFavorite: (Product) -> Unit,
    onBack: () -> Unit,
    loadFailed: Boolean = false,
    // Opens another product page from one of the rails below.
    onProductClick: (Product) -> Unit = {},
    // How many of this item are already in the bag, so the bar can offer to
    // take the customer there instead of adding it a second time.
    quantityInBag: Int = 0,
    onGoToBag: () -> Unit = {},
    // Read by the rails so their own cards can show "in bag" instead of ADD.
    cartQuantities: Map<String, Int> = emptyMap()
) {
    if (product == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Background),
            contentAlignment = Alignment.Center
        ) {
            if (loadFailed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.pd_load_error),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack) {
                        Text(
                            stringResource(R.string.pd_go_back),
                            color = MyntraPink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                CircularProgressIndicator(color = MyntraPink)
            }
        }
        return
    }

    val context = LocalContext.current
    var quantity by remember(product.id) { mutableStateOf(1) }
    val inStock = product.stock > 0

    // ── Everything the page fetches for itself ──────────────────────────
    // Each piece is independent: one failing (offline, an empty coupons
    // table) must not blank the rest of the page, so each has its own
    // fallback and the sections simply render less.
    var categoryName by remember(product.id) { mutableStateOf<String?>(null) }
    var offers by remember(product.id) { mutableStateOf<List<Coupon>>(emptyList()) }
    var similarProducts by remember(product.id) { mutableStateOf<List<Product>>(emptyList()) }
    var storeProducts by remember(product.id) { mutableStateOf<List<Product>>(emptyList()) }

    LaunchedEffect(product.id, product.categoryId, product.sellerId) {
        categoryName = null
        offers = emptyList()
        similarProducts = emptyList()
        storeProducts = emptyList()

        if (product.categoryId.isNotBlank()) {
            CategoryRepository().getCategoryById(product.categoryId)
                .getOrNull()?.let { categoryName = it.name }

            ProductRepository()
                .getProductsPage(page = 0, pageSize = 14, categoryId = product.categoryId)
                .getOrNull()
                ?.filter { it.id != product.id && it.isActive && it.stock > 0 }
                ?.let { similarProducts = it.take(10) }
        }

        if (product.sellerId.isNotBlank()) {
            ProductRepository().getProductsBySeller(product.sellerId)
                .getOrNull()
                ?.filter { it.id != product.id && it.isActive && it.stock > 0 }
                ?.let { storeProducts = it.take(10) }
        }

        OfferRepository().getOffers()
            .getOrNull()
            ?.sortedBy { it.minOrderValue }
            ?.let { offers = it.take(6) }
    }

    // One pass over the reviews, shared by the title block and the ratings
    // section rather than each recomputing it.
    val averageRating = remember(reviews) {
        if (reviews.isEmpty()) null else reviews.sumOf { it.rating } / reviews.size.toDouble()
    }
    val writtenReviews = remember(reviews) { reviews.count { it.comment.isNotBlank() } }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        PdpTopBar(
            isFavorite = isFavorite,
            onBack = onBack,
            onShare = { shareProduct(context, product) },
            onToggleFavorite = { onToggleFavorite(product) }
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item(contentType = "hero") { ProductHero(product) }

                item(contentType = "title") {
                    PdpTitleBlock(
                        product = product,
                        averageRating = averageRating,
                        ratingCount = reviews.size,
                        reviewCount = writtenReviews
                    )
                }

                // A voucher code is only worth showing once the customer can
                // use it, or at least read what it needs — and never on a
                // product there is no stock of.
                if (inStock && offers.isNotEmpty()) {
                    item(contentType = "offers") {
                        PdpOffersRail(offers = offers, productPrice = product.effectivePrice())
                    }
                }

                item(contentType = "specs") {
                    PdpSpecTable(product = product, categoryName = categoryName)
                }

                item(contentType = "services") {
                    PdpDeliveryServices(etaText = estimatedDeliveryWindow())
                }

                item(contentType = "description") { PdpDescription(product) }

                item(contentType = "reviews") { PdpRatingsReviews(reviews = reviews) }

                if (similarProducts.isNotEmpty()) {
                    item(contentType = "similar") {
                        PdpProductRail(
                            title = stringResource(R.string.pd_similar_products),
                            products = similarProducts,
                            cartQuantities = cartQuantities,
                            onAdd = { onAddToCart(it, 1) },
                            onProductClick = onProductClick
                        )
                    }
                }

                if (storeProducts.isNotEmpty()) {
                    item(contentType = "store") {
                        PdpProductRail(
                            title = stringResource(R.string.pd_more_from_store),
                            products = storeProducts,
                            cartQuantities = cartQuantities,
                            onAdd = { onAddToCart(it, 1) },
                            onProductClick = onProductClick
                        )
                    }
                }
            }
        }

        BuyBar(
            product = product,
            quantity = quantity,
            inStock = inStock,
            quantityInBag = quantityInBag,
            onDecrease = { if (quantity > 1) quantity-- },
            onIncrease = { if (quantity < product.stock) quantity++ },
            onAddToCart = { onAddToCart(product, quantity) },
            onGoToBag = onGoToBag
        )
    }
}

/** The photo well: a light stage so the product's own colours carry it. */
@Composable
private fun ProductHero(product: Product) {
    val images = product.images()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(SurfaceWhite),
        contentAlignment = Alignment.Center
    ) {
        if (images.isEmpty()) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = TextLight
            )
        } else {
            ProductImageCarousel(
                images = images,
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                imageModifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
            )
        }
    }
}

/**
 * The pinned buy bar. Once the item is in the bag the primary action becomes
 * a trip to the bag rather than a second "add", so the bar can never be used
 * to add the same thing twice by accident — the same call Myntra's product
 * page makes.
 */
@Composable
private fun BuyBar(
    product: Product,
    quantity: Int,
    inStock: Boolean,
    quantityInBag: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onAddToCart: () -> Unit,
    onGoToBag: () -> Unit
) {
    Surface(color = SurfaceWhite, shadowElevation = 16.dp) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerGray)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Only offered while this would be a fresh add. Once the item
                // is in the bag the count there is the one that matters, and
                // the bag screen owns changing it — a stepper here that no
                // longer fed the button would just be a dead control.
                if (inStock && quantityInBag == 0) {
                    QuantityStepper(
                        quantity = quantity,
                        onDecrease = onDecrease,
                        onIncrease = onIncrease
                    )
                    Spacer(Modifier.width(14.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.pd_total),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "₹${trimAmount(product.effectivePrice() * quantity)}",
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (quantityInBag > 0) {
                        Text(
                            text = stringResource(R.string.pd_in_bag, quantityInBag),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MyntraPink
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = { if (quantityInBag > 0) onGoToBag() else onAddToCart() },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MyntraPink,
                        disabledContainerColor = SurfaceMuted
                    ),
                    enabled = inStock
                ) {
                    if (inStock && quantityInBag == 0) {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        text = when {
                            !inStock -> stringResource(R.string.pd_out_of_stock)
                            quantityInBag > 0 -> stringResource(R.string.pd_go_to_bag)
                            else -> stringResource(R.string.pd_add_to_bag)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (inStock) Color.White else TextLight
                    )
                }
            }
        }
    }
}

/** Square − / count / + control for how many go in the bag. */
@Composable
private fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(Icons.Default.Remove, stringResource(R.string.pd_decrease), onDecrease)
            Text(
                text = "$quantity",
                modifier = Modifier.widthIn(min = 26.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            StepperButton(Icons.Default.Add, stringResource(R.string.pd_increase), onIncrease)
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .dugguClickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = MyntraPink, modifier = Modifier.size(17.dp))
    }
}

/** Shares the product's name, price and where to find it. */
private fun shareProduct(context: android.content.Context, product: Product) {
    val priceLine = if (product.hasDiscount()) {
        "₹${trimAmount(product.effectivePrice())} (${discountPercent(product)}% off, MRP ₹${trimAmount(product.price)})"
    } else {
        "₹${trimAmount(product.price)}"
    }
    val text = buildString {
        append(product.name)
        if (product.unit.isNotBlank()) append(" · ${product.unit}")
        append("\n$priceLine")
        append("\n\nFound on ${context.getString(R.string.app_name)}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, product.name)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.pd_share_title)))
}
