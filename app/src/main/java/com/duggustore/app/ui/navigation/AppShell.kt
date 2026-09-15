package com.duggustore.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.duggustore.app.R
import com.duggustore.app.data.model.UserRole
import com.duggustore.app.ui.components.BottomNavItem

/**
 * Myntra-style bottom bar for customers: Home, Categories, Wishlist, Bag,
 * Profile. Other roles keep their own tab sets.
 */
object BottomNav {

    val CART_KEY = "cart"

    @Composable
    fun itemsFor(role: UserRole): List<BottomNavItem> = when (role) {
        UserRole.CUSTOMER -> listOf(
            BottomNavItem(Screen.CustomerHome.route, stringResource(R.string.nav_home), Icons.Outlined.Home),
            BottomNavItem(Screen.CustomerCategories.route, stringResource(R.string.nav_categories), Icons.Outlined.GridView),
            BottomNavItem(Screen.CustomerFavorites.route, stringResource(R.string.nav_favourites), Icons.Outlined.FavoriteBorder),
            BottomNavItem(Screen.CustomerCart.route, stringResource(R.string.nav_cart), Icons.Outlined.LocalMall),
            BottomNavItem(Screen.CustomerAccount.route, stringResource(R.string.nav_account), Icons.Outlined.Person)
        )
        UserRole.SELLER -> listOf(
            BottomNavItem("0", stringResource(R.string.nav_products), Icons.Default.Inventory),
            BottomNavItem("1", stringResource(R.string.nav_orders), Icons.Default.Receipt)
        )
        UserRole.DELIVERY -> listOf(
            BottomNavItem("0", stringResource(R.string.nav_available), Icons.Default.ShoppingBag),
            BottomNavItem("1", stringResource(R.string.nav_active), Icons.Default.LocalShipping),
            BottomNavItem("2", stringResource(R.string.nav_completed), Icons.Default.Receipt)
        )
        UserRole.ADMIN -> listOf(
            BottomNavItem("0", stringResource(R.string.nav_overview), Icons.Outlined.GridView),
            BottomNavItem("1", stringResource(R.string.nav_users), Icons.Outlined.Person),
            BottomNavItem("2", stringResource(R.string.nav_orders), Icons.Default.Receipt),
            BottomNavItem("3", stringResource(R.string.nav_products), Icons.Default.Inventory),
            BottomNavItem("4", stringResource(R.string.nav_approvals), Icons.Default.VerifiedUser),
            BottomNavItem("5", stringResource(R.string.nav_database), Icons.Default.Storage)
        )
    }

    /**
     * Routes that carry the bar. Detail, form and checkout screens are pushes on
     * top of these — a tab bar there would offer to navigate away mid-task — so
     * they keep their back arrow instead.
     */
    private val CUSTOMER_TOP_LEVEL = setOf(
        Screen.CustomerHome.route,
        Screen.CustomerCategories.route,
        Screen.CustomerFavorites.route,
        Screen.CustomerAccount.route,
        Screen.CustomerOrders.route,
        Screen.CustomerCart.route,
        Screen.ForgotPassword.route
    )

    private val DASHBOARD_ROUTES = setOf(
        Screen.SellerDashboard.route,
        Screen.DeliveryDashboard.route,
        Screen.AdminDashboard.route
    )

    fun showsBar(route: String?, role: UserRole): Boolean = when (role) {
        UserRole.CUSTOMER -> route in CUSTOMER_TOP_LEVEL
        else -> route in DASHBOARD_ROUTES
    }

    /**
     * Which item reads as selected. Orders and Cart are reached from elsewhere and
     * are not themselves tabs, so Orders highlights Account (where it is reached from).
     */
    fun selectedKeyFor(route: String?): String? = when (route) {
        Screen.CustomerOrders.route -> Screen.CustomerAccount.route
        Screen.CustomerCart.route -> Screen.CustomerCart.route
        else -> route
    }
}
