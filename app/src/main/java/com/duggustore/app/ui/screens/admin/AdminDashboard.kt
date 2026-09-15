package com.duggustore.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duggustore.app.data.model.Campaign
import com.duggustore.app.data.model.HomeSection
import com.duggustore.app.data.model.Category
import com.duggustore.app.data.model.Coupon
import com.duggustore.app.data.model.DeliveryPartner
import com.duggustore.app.data.model.DeliveryPartnerDocument
import com.duggustore.app.data.model.DbHealth
import com.duggustore.app.data.model.TableStat
import com.duggustore.app.data.model.Order
import com.duggustore.app.data.model.OrderIssue
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.Seller
import com.duggustore.app.data.model.SellerDocument
import com.duggustore.app.data.model.SponsoredSlot
import com.duggustore.app.data.model.UserProfile
import com.duggustore.app.data.model.VerificationStatus
import com.duggustore.app.ui.components.*
import com.duggustore.app.ui.screens.seller.IssuesList
import com.duggustore.app.ui.theme.*

private val ROLES = listOf("customer", "seller", "delivery", "admin")

@Composable
fun AdminDashboard(
    /** Driven by the bottom bar, which is the only tab control now. */
    selectedTab: Int,
    users: List<UserProfile>,
    orders: List<Order>,
    products: List<Product>,
    totalUsers: Int,
    totalOrders: Int,
    totalRevenue: Double,
    totalDeliveries: Int,
    onUpdateUserRole: (String, String) -> Unit,
    /** Unfiltered — [AdminApprovalsScreen] narrows to the ones actually awaiting a decision. */
    allSellers: List<Seller>,
    sellerDocuments: Map<String, List<SellerDocument>>,
    sellerDocumentUrls: Map<String, String>,
    loadingSellerDocsFor: String?,
    onLoadSellerDocuments: (String) -> Unit,
    onReviewSeller: (String, Boolean, String) -> Unit,
    reviewingSellerId: String?,
    sellerReviewError: String?,
    onClearSellerReviewError: () -> Unit,
    onBlockSeller: (String) -> Unit,
    onUnblockSeller: (String) -> Unit,
    onDeleteSeller: (String) -> Unit,
    onPurgeSeller: (String) -> Unit,
    onPromoteToSeller: (String) -> Unit,
    managingSellerId: String?,
    sellerManageError: String?,
    onClearSellerManageError: () -> Unit,
    allPartners: List<DeliveryPartner>,
    partnerDocuments: Map<String, List<DeliveryPartnerDocument>>,
    partnerDocumentUrls: Map<String, String>,
    loadingPartnerDocsFor: String?,
    onLoadPartnerDocuments: (String) -> Unit,
    onReviewPartner: (String, Boolean, String) -> Unit,
    reviewingPartnerId: String?,
    partnerReviewError: String?,
    onClearPartnerReviewError: () -> Unit,
    issues: List<OrderIssue>,
    onResolveIssue: (issueId: String, approve: Boolean, refundAmount: Int) -> Unit,
    categories: List<Category>,
    coupons: List<Coupon>,
    campaigns: List<Campaign>,
    isSavingCatalog: Boolean,
    catalogError: String?,
    onClearCatalogError: () -> Unit,
    onToggleProductActive: (Product) -> Unit,
    onSaveCategory: (Category) -> Unit,
    onToggleCategoryActive: (Category) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveCoupon: (Coupon) -> Unit,
    onToggleCouponActive: (Coupon) -> Unit,
    onDeleteCoupon: (String) -> Unit,
    onSaveCampaign: (Campaign, Int) -> Unit,
    onToggleCampaignActive: (Campaign) -> Unit,
    onDeleteCampaign: (String) -> Unit,
    homeSections: List<HomeSection> = emptyList(),
    sectionCategories: Map<String, List<String>> = emptyMap(),
    onSaveHomeSection: (HomeSection, List<String>) -> Unit = { _, _ -> },
    onToggleHomeSectionActive: (HomeSection) -> Unit = {},
    onDeleteHomeSection: (String) -> Unit = {},
    sponsoredSlots: List<SponsoredSlot>,
    onReviewSponsoredSlot: (String, Boolean, String) -> Unit,
    reviewingSlotId: String?,
    slotReviewError: String?,
    onClearSlotReviewError: () -> Unit,
    dbHealth: DbHealth? = null,
    tableStats: List<TableStat> = emptyList(),
    isLoadingDb: Boolean = false,
    dbError: String? = null,
    onRefreshDb: () -> Unit = {},
    onClearDbError: () -> Unit = {},
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        DashboardHeader(
            title = "Admin",
            subtitle = "Everything across the store",
            stats = listOf(
                "Revenue" to "₹${trimAmount(totalRevenue)}",
                "Orders" to "$totalOrders",
                "Users" to "$totalUsers",
                "Delivered" to "$totalDeliveries"
            ),
            onSignOut = onSignOut
        )

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> OverviewTab(orders = orders, products = products)
                1 -> UsersTab(
                    users = users,
                    sellers = allSellers,
                    onUpdateUserRole = onUpdateUserRole,
                    onPromoteToSeller = onPromoteToSeller,
                    onBlockSeller = onBlockSeller,
                    onUnblockSeller = onUnblockSeller,
                    onDeleteSeller = onDeleteSeller,
                    onPurgeSeller = onPurgeSeller,
                    managingSellerId = managingSellerId,
                    sellerManageError = sellerManageError,
                    onClearSellerManageError = onClearSellerManageError
                )
                2 -> OrdersTab(orders = orders, issues = issues, onResolveIssue = onResolveIssue)
                3 -> AdminCatalogScreen(
                    products = products,
                    categories = categories,
                    coupons = coupons,
                    campaigns = campaigns,
                    isSaving = isSavingCatalog,
                    catalogError = catalogError,
                    onClearError = onClearCatalogError,
                    onToggleProductActive = onToggleProductActive,
                    onSaveCategory = onSaveCategory,
                    onToggleCategoryActive = onToggleCategoryActive,
                    onDeleteCategory = onDeleteCategory,
                    onSaveCoupon = onSaveCoupon,
                    onToggleCouponActive = onToggleCouponActive,
                    onDeleteCoupon = onDeleteCoupon,
                    onSaveCampaign = onSaveCampaign,
                    onToggleCampaignActive = onToggleCampaignActive,
                    onDeleteCampaign = onDeleteCampaign,
                    homeSections = homeSections,
                    sectionCategories = sectionCategories,
                    onSaveHomeSection = onSaveHomeSection,
                    onToggleHomeSectionActive = onToggleHomeSectionActive,
                    onDeleteHomeSection = onDeleteHomeSection
                )
                5 -> AdminDatabaseScreen(
                    health = dbHealth,
                    tableStats = tableStats,
                    isLoading = isLoadingDb,
                    error = dbError,
                    onRefresh = onRefreshDb,
                    onClearError = onClearDbError
                )
                else -> AdminApprovalsScreen(
                    sellers = allSellers,
                    sellerDocuments = sellerDocuments,
                    sellerDocumentUrls = sellerDocumentUrls,
                    loadingSellerDocsFor = loadingSellerDocsFor,
                    onLoadSellerDocuments = onLoadSellerDocuments,
                    onReviewSeller = onReviewSeller,
                    reviewingSellerId = reviewingSellerId,
                    sellerReviewError = sellerReviewError,
                    onClearSellerReviewError = onClearSellerReviewError,
                    partners = allPartners,
                    partnerDocuments = partnerDocuments,
                    partnerDocumentUrls = partnerDocumentUrls,
                    loadingPartnerDocsFor = loadingPartnerDocsFor,
                    onLoadPartnerDocuments = onLoadPartnerDocuments,
                    onReviewPartner = onReviewPartner,
                    reviewingPartnerId = reviewingPartnerId,
                    partnerReviewError = partnerReviewError,
                    onClearPartnerReviewError = onClearPartnerReviewError,
                    sponsoredSlots = sponsoredSlots,
                    onReviewSponsoredSlot = onReviewSponsoredSlot,
                    reviewingSlotId = reviewingSlotId,
                    slotReviewError = slotReviewError,
                    onClearSlotReviewError = onClearSlotReviewError
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(orders: List<Order>, products: List<Product>) {
    if (orders.isEmpty() && products.isEmpty()) {
        DashboardEmpty(
            icon = Icons.Default.Inventory,
            title = "Nothing to show yet",
            subtitle = "Orders and products will appear here as the store is used"
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (orders.isNotEmpty()) {
            item { GroupTitle("Recent orders") }
            items(orders.take(5), key = { "recent-${it.id}" }) { OrderRow(it) }
        }
        if (products.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                GroupTitle("Products")
            }
            items(products.take(5), key = { "top-${it.id}" }) { ProductRow(it) }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<UserProfile>,
    sellers: List<Seller>,
    onUpdateUserRole: (String, String) -> Unit,
    onPromoteToSeller: (String) -> Unit,
    onBlockSeller: (String) -> Unit,
    onUnblockSeller: (String) -> Unit,
    onDeleteSeller: (String) -> Unit,
    onPurgeSeller: (String) -> Unit,
    managingSellerId: String?,
    sellerManageError: String?,
    onClearSellerManageError: () -> Unit
) {
    if (users.isEmpty()) {
        DashboardEmpty(
            icon = Icons.Default.Person,
            title = "No users",
            subtitle = "Accounts will appear here as people sign up"
        )
        return
    }
    val sellerById = remember(sellers) { sellers.associateBy { it.id } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (sellerManageError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = CoralSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sellerManageError, modifier = Modifier.weight(1f), color = CoralDark, fontSize = 13.sp)
                    Text(
                        "Dismiss",
                        color = CoralDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp).clickable { onClearSellerManageError() }
                    )
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users, key = { it.id }) { user ->
                UserManagementCard(
                    user = user,
                    seller = sellerById[user.id],
                    isManagingSeller = managingSellerId == user.id,
                    onUpdateRole = { role ->
                        if (role == "seller") onPromoteToSeller(user.id) else onUpdateUserRole(user.id, role)
                    },
                    onBlockSeller = { onBlockSeller(user.id) },
                    onUnblockSeller = { onUnblockSeller(user.id) },
                    onDeleteSeller = { onDeleteSeller(user.id) },
                    onPurgeSeller = { onPurgeSeller(user.id) }
                )
            }
        }
    }
}

@Composable
private fun OrdersTab(
    orders: List<Order>,
    issues: List<OrderIssue>,
    onResolveIssue: (issueId: String, approve: Boolean, refundAmount: Int) -> Unit
) {
    var showIssues by rememberSaveable { mutableStateOf(false) }
    val openIssueCount = issues.count { it.status == "open" }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OrdersTabChip("All orders (${orders.size})", !showIssues) { showIssues = false }
            OrdersTabChip(
                if (openIssueCount > 0) "Issues ($openIssueCount)" else "Issues",
                showIssues
            ) { showIssues = true }
        }

        if (!showIssues) {
            if (orders.isEmpty()) {
                DashboardEmpty(
                    icon = Icons.Default.Receipt,
                    title = "No orders",
                    subtitle = "Orders placed in the store will appear here"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(orders, key = { it.id }) { OrderRow(order = it, showDate = true) }
                }
            }
        } else {
            IssuesList(issues = issues, onResolve = onResolveIssue, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OrdersTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Teal else SurfaceMuted,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(bottom = 2.dp),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
}

@Composable
private fun OrderRow(order: Order, showDate: Boolean = false) {
    DashboardPanel {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${order.id.takeLast(8).uppercase()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "₹${trimAmount(order.totalAmount)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )
                if (showDate) {
                    Text(order.createdAt.take(10), fontSize = 11.sp, color = TextLight)
                }
            }
            StatusBadge(status = order.status)
        }
    }
}

@Composable
private fun ProductRow(product: Product) {
    DashboardPanel {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl.isNullOrBlank()) {
                    Icon(
                        Icons.Default.ShoppingBag,
                        null,
                        tint = TextLight,
                        modifier = Modifier.size(21.dp)
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "₹${trimAmount(product.effectivePrice())} · stock ${product.stock}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            if (!product.isActive) {
                Surface(shape = RoundedCornerShape(7.dp), color = CoralSurface) {
                    Text(
                        text = "INACTIVE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoralDark
                    )
                }
            }
        }
    }
}

@Composable
fun UserManagementCard(
    user: UserProfile,
    onUpdateRole: (String) -> Unit,
    seller: Seller? = null,
    isManagingSeller: Boolean = false,
    onBlockSeller: () -> Unit = {},
    onUnblockSeller: () -> Unit = {},
    onDeleteSeller: () -> Unit = {},
    onPurgeSeller: () -> Unit = {}
) {
    var showRoleMenu by remember { mutableStateOf(false) }
    var showSellerMenu by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<SellerConfirmAction?>(null) }
    val (bg, fg) = roleColors(user.role)

    DashboardPanel {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(TealSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.fullName.trim().firstOrNull()?.uppercase() ?: "U",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Teal
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.fullName.takeIf { it.isNotBlank() } ?: "Unnamed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.phone.takeIf { it.isNotBlank() } ?: "No phone on file",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable { showRoleMenu = true }
                            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = user.role.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Change role",
                            tint = fg,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showRoleMenu,
                        onDismissRequest = { showRoleMenu = false }
                    ) {
                        ROLES.forEach { role ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = role.replaceFirstChar { it.uppercase() },
                                        fontWeight = if (role == user.role) FontWeight.Bold
                                                     else FontWeight.Normal,
                                        color = if (role == user.role) Teal else TextPrimary
                                    )
                                },
                                onClick = {
                                    showRoleMenu = false
                                    // Re-sending the role the user already has is a
                                    // write for nothing.
                                    if (role != user.role) onUpdateRole(role)
                                }
                            )
                        }
                    }
                }

                // These actions all address a row in the sellers table by id, so
                // they need an actual seller record — not just the "seller" role
                // on the profile. A profile can carry the role without one (the
                // role dropdown writes profiles.role directly), and acting on
                // that used to send the RPC an id it couldn't resolve, which
                // came back as "Seller not found".
                if (seller != null) {
                    Box {
                        if (isManagingSeller) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(start = 4.dp).size(20.dp),
                                strokeWidth = 2.dp,
                                color = Teal
                            )
                        } else {
                            IconButton(onClick = { showSellerMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Manage seller", tint = TextSecondary)
                            }
                        }
                        DropdownMenu(expanded = showSellerMenu, onDismissRequest = { showSellerMenu = false }) {
                            val status = seller.verificationStatus()
                            if (status == VerificationStatus.SUSPENDED) {
                                DropdownMenuItem(
                                    text = { Text("Unblock seller") },
                                    leadingIcon = { Icon(Icons.Default.LockOpen, null, tint = SuccessGreen) },
                                    onClick = { showSellerMenu = false; onUnblockSeller() }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Block seller") },
                                    leadingIcon = { Icon(Icons.Default.Block, null, tint = CoralDark) },
                                    onClick = { showSellerMenu = false; confirmAction = SellerConfirmAction.BLOCK }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete seller") },
                                leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = CoralDark) },
                                onClick = { showSellerMenu = false; confirmAction = SellerConfirmAction.DELETE }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove completely", color = CoralDark, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = CoralDark) },
                                onClick = { showSellerMenu = false; confirmAction = SellerConfirmAction.PURGE }
                            )
                        }
                    }
                }
            }

            if (seller != null) {
                Spacer(Modifier.height(8.dp))
                SellerStatusBadge(seller.verificationStatus())
            } else if (user.role == "seller") {
                // Flags the mismatch instead of offering actions that cannot work.
                Spacer(Modifier.height(8.dp))
                Text(
                    "Seller role, but no seller record — re-apply the Seller role to create one.",
                    fontSize = 11.sp,
                    color = TextLight
                )
            }
        }
    }

    confirmAction?.let { action ->
        SellerActionConfirmDialog(
            action = action,
            sellerName = seller?.businessName?.takeIf { it.isNotBlank() } ?: user.fullName.takeIf { it.isNotBlank() } ?: "this seller",
            onDismiss = { confirmAction = null },
            onConfirm = {
                confirmAction = null
                when (action) {
                    SellerConfirmAction.BLOCK -> onBlockSeller()
                    SellerConfirmAction.DELETE -> onDeleteSeller()
                    SellerConfirmAction.PURGE -> onPurgeSeller()
                }
            }
        )
    }
}

private enum class SellerConfirmAction { BLOCK, DELETE, PURGE }

@Composable
private fun SellerStatusBadge(status: VerificationStatus) {
    val (bg, fg, label) = when (status) {
        VerificationStatus.SUSPENDED -> Triple(CoralSurface, CoralDark, "Blocked")
        VerificationStatus.REJECTED -> Triple(SurfaceMuted, TextLight, "Removed")
        VerificationStatus.APPROVED -> Triple(TealSurface, TealDark, "Active")
        VerificationStatus.UNDER_REVIEW -> Triple(OrangeSurface, OrangeDark, "Under review")
        VerificationStatus.PENDING_VERIFICATION -> Triple(SurfaceMuted, TextLight, "Not submitted")
    }
    Surface(shape = RoundedCornerShape(7.dp), color = bg) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

/**
 * Block and delete are reversible (an admin can flip them back), so a light confirm
 * is enough. "Remove completely" is not — it wipes the account, products and order
 * history for good — so it asks the admin to type the business name back before the
 * confirm button lights up, the same friction a real "delete my account" flow uses.
 */
@Composable
private fun SellerActionConfirmDialog(
    action: SellerConfirmAction,
    sellerName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title: String
    val body: String
    val confirmLabel: String
    when (action) {
        SellerConfirmAction.BLOCK -> {
            title = "Block $sellerName?"
            body = "They won't be able to sell and their products are hidden from customers until an admin unblocks them."
            confirmLabel = "Block"
        }
        SellerConfirmAction.DELETE -> {
            title = "Delete $sellerName?"
            body = "Their products are hidden and they lose seller access. Their account and order history stay on file, and this can be undone by an admin later."
            confirmLabel = "Delete"
        }
        SellerConfirmAction.PURGE -> {
            title = "Remove $sellerName completely?"
            body = "This permanently deletes their account, every product, and their entire order history. This cannot be undone."
            confirmLabel = "Remove permanently"
        }
    }

    if (action == SellerConfirmAction.PURGE) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    Text(body, fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Text("Type \"$sellerName\" to confirm", fontSize = 12.sp, color = TextLight)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = typed == sellerName) {
                    Text(confirmLabel, color = CoralDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(body, fontSize = 13.sp, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = CoralDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

private fun roleColors(role: String): Pair<Color, Color> = when (role) {
    "admin" -> CoralSurface to CoralDark
    "seller" -> TealSurface to TealDark
    "delivery" -> Color(0xFFE3F0FD) to InfoBlue
    else -> OrangeSurface to OrangeDark
}
