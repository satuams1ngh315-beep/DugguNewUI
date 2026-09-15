package com.duggustore.app.ui.screens.seller

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.duggustore.app.data.model.Order
import com.duggustore.app.data.model.Category
import com.duggustore.app.data.model.OrderItem
import com.duggustore.app.data.model.OrderStatus
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.SponsoredSlot
import com.duggustore.app.platform.LocationState
import com.duggustore.app.platform.openDialer
import com.duggustore.app.platform.rememberDeviceLocation
import com.duggustore.app.R
import com.duggustore.app.ui.components.*
import com.duggustore.app.ui.theme.*

/**
 * New orders are read out loud on the alarm stream so they carry across a
 * shop, which is exactly why it needs to be one tap to silence.
 */
@Composable
private fun VoiceAlertToggle(enabled: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (enabled) TealSurface else SurfaceMuted
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (enabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                contentDescription = null,
                tint = if (enabled) TealDark else TextLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(
                    if (enabled) R.string.seller_alerts_on else R.string.seller_alerts_off
                ),
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) TealDark else TextSecondary
            )
            Text(
                text = stringResource(
                    if (enabled) R.string.seller_alerts_on_action else R.string.seller_alerts_off_action
                ),
                modifier = Modifier.clickable { onToggle() },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Teal
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SellerDashboard(
    /** Driven by the bottom bar, which is the only tab control now. */
    selectedTab: Int,
    products: List<Product>,
    orders: List<Order>,
    isLoading: Boolean = false,
    onRefreshOrders: () -> Unit = {},
    totalRevenue: Double,
    totalOrders: Int,
    onAddProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    /**
     * Quick sell: the categories a seller can list into, shown as tiles that
     * open the product form with that category already chosen. Empty simply
     * hides the card, so a store with no categories still renders.
     */
    categories: List<Category> = emptyList(),
    onQuickSell: (String) -> Unit = {},
    onDeleteProduct: (String) -> Unit,
    onUpdateOrderStatus: (String, OrderStatus) -> Unit,
    orderItemsByOrderId: Map<String, List<OrderItem>> = emptyMap(),
    onExpandOrderItems: (String) -> Unit = {},
    hasStoreLocation: Boolean = true,
    onSaveStoreLocation: (String, Double, Double) -> Unit = { _, _, _ -> },
    openIssuesCount: Int = 0,
    onIssuesClick: () -> Unit = {},
    voiceAlertsEnabled: Boolean = true,
    onToggleVoiceAlerts: () -> Unit = {},
    sponsoredSlots: List<SponsoredSlot> = emptyList(),
    // productId, durationDays, an optional short note.
    onRequestSponsoredSlot: (String, Int, String) -> Unit = { _, _, _ -> },
    quoteSlotFee: (Int) -> Int = { it * 5 },
    isRequestingSlot: Boolean = false,
    slotRequestError: String? = null,
    onClearSlotRequestError: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val pendingCount = orders.count { it.status == OrderStatus.PENDING.value }
    var showSlotRequestDialog by remember { mutableStateOf(false) }
    // Newest first, so a rejected old request doesn't hide a fresher pending
    // one — only the most recent request's state is worth showing.
    val latestSlot = remember(sponsoredSlots) { sponsoredSlots.maxByOrNull { it.createdAt } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        DashboardHeader(
            title = "Seller dashboard",
            subtitle = if (pendingCount == 0) "No orders waiting on you"
                       else if (pendingCount == 1) "1 order waiting on you"
                       else "$pendingCount orders waiting on you",
            stats = listOf(
                "Revenue" to "₹${trimAmount(totalRevenue)}",
                "Orders" to "$totalOrders",
                "Products" to "${products.size}"
            ),
            issuesBadgeCount = openIssuesCount,
            onIssuesClick = onIssuesClick,
            onSignOut = onSignOut
        )

        // Without this, delivery_id gets set on their orders but no rider has
        // any idea where the store actually is — nothing in the app ever
        // asked. Stays up until it's saved once.
        if (!hasStoreLocation) {
            StoreLocationBanner(onSave = onSaveStoreLocation)
        }

        when (selectedTab) {
            0 -> Box(modifier = Modifier.weight(1f)) {
                if (products.isEmpty()) {
                    // Quick sell stays up on an empty store: "no products
                    // yet" is precisely the moment a category shortcut is
                    // worth the most.
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (categories.isNotEmpty()) {
                            QuickSellCard(
                                categories = categories,
                                onQuickSell = onQuickSell,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DashboardEmpty(
                                icon = Icons.Default.Inventory,
                                title = "No products yet",
                                subtitle = "Add your first product to start selling"
                            )
                        }
                    }
                } else {
                    val lowStock = products.count { it.stock in 1..9 }
                    val outOfStock = products.count { it.stock <= 0 }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (categories.isNotEmpty()) {
                            item {
                                QuickSellCard(
                                    categories = categories,
                                    onQuickSell = onQuickSell
                                )
                            }
                        }
                        item {
                            PromoteStoreBanner(
                                latestSlot = latestSlot,
                                onRequest = { showSlotRequestDialog = true }
                            )
                        }
                        if (lowStock > 0 || outOfStock > 0) {
                            item { LowStockBanner(lowStock = lowStock, outOfStock = outOfStock) }
                        }
                        items(products, key = { it.id }) { product ->
                            ProductManagementCard(
                                product = product,
                                onEdit = { onEditProduct(product.id) },
                                onDelete = { onDeleteProduct(product.id) }
                            )
                        }
                    }
                }

                ExtendedFloatingActionButton(
                    onClick = onAddProduct,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    containerColor = Orange,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add product", fontWeight = FontWeight.Bold)
                }
            }

            else -> {
                val ordersPullRefreshState = rememberPullRefreshState(refreshing = isLoading, onRefresh = onRefreshOrders)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pullRefresh(ordersPullRefreshState)
                ) {
                    if (orders.isEmpty()) {
                        DashboardEmpty(
                            icon = Icons.Default.Receipt,
                            title = "No orders yet",
                            subtitle = "Orders from customers will appear here"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                VoiceAlertToggle(
                                    enabled = voiceAlertsEnabled,
                                    onToggle = onToggleVoiceAlerts
                                )
                            }
                            item { WeeklyRevenueCard(orders = orders) }
                            item { PayoutSummaryCard(orders = orders) }
                            items(orders, key = { it.id }) { order ->
                                OrderManagementCard(
                                    order = order,
                                    items = orderItemsByOrderId[order.id] ?: emptyList(),
                                    onUpdateStatus = { status -> onUpdateOrderStatus(order.id, status) },
                                    onExpandItems = { onExpandOrderItems(order.id) }
                                )
                            }
                        }
                    }

                    PullRefreshIndicator(
                        refreshing = isLoading,
                        state = ordersPullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        contentColor = Teal
                    )
                }
            }
        }
    }

    if (showSlotRequestDialog) {
        SponsoredSlotRequestDialog(
            products = products,
            quoteFee = quoteSlotFee,
            isSaving = isRequestingSlot,
            error = slotRequestError,
            onDismissError = onClearSlotRequestError,
            onDismiss = { showSlotRequestDialog = false },
            onSubmit = { productId, days, note ->
                onRequestSponsoredSlot(productId, days, note)
                showSlotRequestDialog = false
            }
        )
    }
}

/**
 * Shows the seller's own most recent sponsored-slot request — which product
 * it promotes and its state — or a plain call to action when they've never
 * asked for one. Collecting the quoted fee happens outside the app for
 * now; this only files the request, and an admin approving it is what
 * actually puts it live.
 */
@Composable
private fun PromoteStoreBanner(latestSlot: SponsoredSlot?, onRequest: () -> Unit) {
    val (bg, fg, statusLine) = when (latestSlot?.status) {
        "PENDING" -> Triple(
            OrangeSurface, OrangeDark,
            "Waiting on admin review · ₹${latestSlot.feeAmount} for ${latestSlot.durationDays} days once approved"
        )
        "APPROVED" -> Triple(TealSurface, TealDark, "Live until ${latestSlot.endsAt.orEmpty().take(10)}")
        "REJECTED" -> Triple(CoralSurface, CoralDark, "Last request was rejected" +
            (latestSlot.rejectionReason?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""))
        else -> Triple(TealSurface, TealDark, null)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Campaign, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = latestSlot?.product?.name?.takeIf { latestSlot.status != "REJECTED" }
                        ?: "Promote a product",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = fg
                )
                if (statusLine != null) {
                    Text(statusLine, fontSize = 11.sp, color = fg)
                } else {
                    Text("Feature one of your products on the customer home screen", fontSize = 11.sp, color = TextSecondary)
                }
            }
            // A new request is always allowed — even with one already live or
            // pending, e.g. to line up the next one once the current ends.
            Text(
                text = if (latestSlot == null) "Request" else "Request again",
                modifier = Modifier.clickable { onRequest() },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
    }
}

@Composable
private fun SponsoredSlotRequestDialog(
    products: List<Product>,
    quoteFee: (Int) -> Int,
    isSaving: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (productId: String, durationDays: Int, note: String) -> Unit
) {
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var showProductMenu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var durationDays by remember { mutableStateOf("7") }
    val days = durationDays.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promote a product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Featured on the customer home screen once an admin approves it. Nominal fee while the app is new — arranged outside the app.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                if (error != null) {
                    Surface(shape = RoundedCornerShape(10.dp), color = CoralSurface, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, modifier = Modifier.weight(1f), fontSize = 12.sp, color = CoralDark)
                            Text(
                                "Dismiss",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralDark,
                                modifier = Modifier.clickable { onDismissError() }
                            )
                        }
                    }
                }
                Box {
                    OutlinedTextField(
                        value = selectedProduct?.name ?: "Choose a product",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Product") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // OutlinedTextField doesn't take a click when readOnly, so
                    // a transparent catcher on top opens the menu instead.
                    Box(modifier = Modifier.matchParentSize().clickable { showProductMenu = true })
                    DropdownMenu(expanded = showProductMenu, onDismissRequest = { showProductMenu = false }) {
                        products.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name) },
                                onClick = { selectedProduct = product; showProductMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Short note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = durationDays,
                    onValueChange = { input -> if (input.all { it.isDigit() }) durationDays = input },
                    label = { Text("How many days, starting once approved") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (days > 0) {
                    Text("Fee: ₹${quoteFee(days)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TealDark)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedProduct != null && days > 0 && !isSaving,
                onClick = { selectedProduct?.let { onSubmit(it.id, days, note.trim()) } }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Teal)
                } else {
                    Text("Send request", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Quick sell — one tap from the dashboard into a product form that is
 * already filed.
 *
 * Adding a product otherwise starts with the same question every time ("what
 * is this and where does it go"), and on a shop counter that question is
 * usually answered before the app is even open: the seller is standing in
 * front of the shelf they are restocking. Picking the category here removes
 * the one field they would have had to go looking for, and lands them on the
 * same form as ever.
 */
@Composable
private fun QuickSellCard(
    categories: List<Category>,
    onQuickSell: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceWhite)
            .border(0.5.dp, DividerGray, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Orange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.seller_quick_sell_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.seller_quick_sell_subtitle),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .dugguClickableFlat { onQuickSell(category.id) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceMuted)
                            .border(0.5.dp, DividerGray, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconForCategory(category.name),
                            contentDescription = category.name,
                            tint = Orange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = category.name,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ProductManagementCard(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    DashboardPanel {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                // The seller uploads an image; showing a placeholder for every
                // product regardless made the list useless for telling them apart.
                if (product.imageUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Default.ShoppingBag,
                        null,
                        tint = TextLight,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "₹${trimAmount(product.effectivePrice())} · per ${product.unit}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(5.dp))
                StockPill(stock = product.stock)
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit ${product.name}", tint = Teal)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete ${product.name}", tint = Coral)
            }
        }
    }
}

@Composable
private fun StockPill(stock: Int) {
    val (bg, fg, label) = when {
        stock <= 0 -> Triple(CoralSurface, CoralDark, "Out of stock")
        stock < 10 -> Triple(OrangeSurface, OrangeDark, "Low stock · $stock")
        else -> Triple(TealSurface, TealDark, "In stock · $stock")
    }
    Surface(shape = RoundedCornerShape(7.dp), color = bg) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

@Composable
private fun LowStockBanner(lowStock: Int, outOfStock: Int) {
    val message = buildList {
        if (outOfStock > 0) add(if (outOfStock == 1) "1 product is out of stock" else "$outOfStock products are out of stock")
        if (lowStock > 0) add(if (lowStock == 1) "1 running low" else "$lowStock running low")
    }.joinToString(" · ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = OrangeSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Inventory, null, tint = OrangeDark, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OrangeDark)
        }
    }
}

/**
 * The last 7 days' revenue as a simple bar per day — enough to see whether
 * sales are trending up or down without needing a charting library for one
 * small graph. Cancelled orders don't count as revenue; every other status
 * already implies the seller was paid or will be.
 */
@Composable
private fun WeeklyRevenueCard(orders: List<Order>) {
    val days = remember(orders) { lastSevenDaysRevenue(orders) }
    val maxRevenue = days.maxOf { it.second }.coerceAtLeast(1.0)

    DashboardPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Revenue, last 7 days",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { (label, revenue) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((60 * (revenue / maxRevenue)).dp.coerceAtLeast(3.dp))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (revenue > 0) Teal else BorderGray)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(label, fontSize = 10.sp, color = TextLight)
                    }
                }
            }
        }
    }
}

/**
 * A flat platform commission — there's no per-seller rate stored anywhere,
 * so this is the same illustrative figure quoted to every seller rather
 * than a real settlement ledger (no payout table exists yet either).
 */
private const val PLATFORM_COMMISSION_RATE = 0.10

/** What the seller is owed once the platform's cut and the rider's delivery fee are set aside. */
@Composable
private fun PayoutSummaryCard(orders: List<Order>) {
    val delivered = remember(orders) { orders.filter { it.status == OrderStatus.DELIVERED.value } }
    val grossSales = delivered.sumOf { it.totalAmount - it.deliveryFee }
    val commission = grossSales * PLATFORM_COMMISSION_RATE
    val netPayout = grossSales - commission

    DashboardPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Payout summary", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "From ${delivered.size} delivered order${if (delivered.size == 1) "" else "s"}",
                fontSize = 11.sp,
                color = TextLight
            )
            Spacer(Modifier.height(12.dp))
            PayoutRow("Gross sales", grossSales)
            PayoutRow("Platform fee (${(PLATFORM_COMMISSION_RATE * 100).toInt()}%)", -commission, color = Coral)
            Divider(Modifier.padding(vertical = 8.dp), color = BorderGray)
            PayoutRow("You receive", netPayout, bold = true, color = Teal)
        }
    }
}

@Composable
private fun PayoutRow(label: String, amount: Double, bold: Boolean = false, color: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (bold) TextPrimary else TextSecondary,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = if (amount < 0) "-₹${trimAmount(-amount)}" else "₹${trimAmount(amount)}",
            fontSize = if (bold) 16.sp else 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}

/** Oldest to newest, so the bars read left-to-right like a normal chart. */
private fun lastSevenDaysRevenue(orders: List<Order>): List<Pair<String, Double>> {
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val calendar = java.util.Calendar.getInstance()
    val today = calendar.get(java.util.Calendar.DAY_OF_YEAR)
    val year = calendar.get(java.util.Calendar.YEAR)

    val revenueByDate = orders
        .filter { it.status != OrderStatus.CANCELLED.value }
        .groupBy { it.createdAt.take(10) }
        .mapValues { (_, group) -> group.sumOf { it.totalAmount } }

    return (6 downTo 0).map { daysAgo ->
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.DAY_OF_YEAR, today)
            add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        }
        val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val weekday = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        dayLabels[weekday] to (revenueByDate[dateKey] ?: 0.0)
    }
}

/**
 * Prompt the seller taps once to record where their store actually is — a
 * device GPS fix, reverse-geocoded to something readable. Every rider who
 * later claims one of their orders reads this back to navigate to pickup.
 */
@Composable
private fun StoreLocationBanner(onSave: (String, Double, Double) -> Unit) {
    val detected = rememberDeviceLocation(autoStart = false)
    val found = detected.state as? LocationState.Found
    val busy = detected.state is LocationState.Locating

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        color = OrangeSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !busy) {
                    if (found != null) onSave(found.address, found.latitude, found.longitude)
                    else detected.refresh()
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocationOn, null, tint = OrangeDark, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set your store location",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrangeDark
                )
                Text(
                    text = when {
                        busy -> "Finding you…"
                        found != null -> "Tap to save: ${found.address}"
                        else -> "So a rider knows where to pick up your orders"
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = OrangeDark, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
fun OrderManagementCard(
    order: Order,
    items: List<OrderItem> = emptyList(),
    onUpdateStatus: (OrderStatus) -> Unit,
    onExpandItems: () -> Unit = {}
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    DashboardPanel {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${order.id.takeLast(8).uppercase()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                StatusBadge(status = order.status)
            }

            Spacer(Modifier.height(8.dp))

            // What's actually in the order, not just its number — this used
            // to only show once "View items" was tapped.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val firstItem = items.firstOrNull()
                val thumbnailUrl = firstItem?.product?.images()?.firstOrNull()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(SurfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnailUrl != null) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(Icons.Default.ShoppingBag, null, tint = TextLight, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        items.isEmpty() -> "Loading items…"
                        items.size == 1 -> items[0].product?.name ?: "1 item"
                        else -> "${items[0].product?.name ?: "Item"} +${items.size - 1} more"
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "₹${trimAmount(order.totalAmount)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Teal
            )

            if (order.deliveryAddress.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.deliveryAddress,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val phone = order.customer?.phone
                    if (!phone.isNullOrBlank()) {
                        IconButton(onClick = { openDialer(context, phone) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Call, "Call customer", tint = Teal, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        if (expanded) onExpandItems()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Hide items" else "View items",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Teal
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(6.dp))
                if (items.isEmpty()) {
                    Text("Loading items…", fontSize = 12.sp, color = TextLight)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val imageUrl = item.product?.images()?.firstOrNull()
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(SurfaceMuted),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (imageUrl != null) {
                                        AsyncImage(
                                            model = imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().padding(3.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(Icons.Default.ShoppingBag, null, tint = TextLight, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${item.product?.name ?: "Item"} ×${item.quantity}",
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "₹${trimAmount(item.priceAtPurchase * item.quantity)}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Before this, only a pending order had actions, so an order the
            // seller had accepted could never be moved on to preparing or
            // handed to delivery — it sat as "confirmed" forever.
            val next = when (order.status) {
                OrderStatus.CONFIRMED.value -> OrderStatus.PREPARING to "Start preparing"
                OrderStatus.PREPARING.value -> OrderStatus.READY_FOR_PICKUP to "Mark ready for pickup"
                else -> null
            }

            if (order.status == OrderStatus.PENDING.value) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DashboardAction(
                        text = "Accept",
                        color = Teal,
                        onClick = { onUpdateStatus(OrderStatus.CONFIRMED) }
                    )
                    DashboardAction(
                        text = "Reject",
                        color = Coral,
                        filled = false,
                        onClick = { onUpdateStatus(OrderStatus.CANCELLED) }
                    )
                }
            } else if (next != null) {
                Spacer(Modifier.height(12.dp))
                DashboardAction(
                    text = next.second,
                    color = Orange,
                    onClick = { onUpdateStatus(next.first) }
                )
            }
        }
    }
}
