package com.duggustore.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.duggustore.app.data.local.AppPrefs
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.Activity
import com.duggustore.app.data.repository.RazorpayOrderRepository
import com.duggustore.app.platform.PushNotifications
import com.duggustore.app.platform.RazorpayPay
import com.duggustore.app.platform.RazorpayResult
import com.duggustore.app.platform.RazorpayResultBus
import com.duggustore.app.ui.theme.appPushEnter
import com.duggustore.app.ui.theme.appPushExit
import com.duggustore.app.ui.theme.appPushPopEnter
import com.duggustore.app.ui.theme.appPushPopExit
import com.duggustore.app.ui.theme.appTabEnter
import com.duggustore.app.ui.theme.appTabExit
import com.duggustore.app.platform.RiderLocationPublisher
import com.duggustore.app.platform.rememberOrderAnnouncer
import com.duggustore.app.platform.rememberRiderPosition
import kotlin.math.roundToInt
import com.duggustore.app.ui.components.StoreBottomBar
import com.duggustore.app.ui.components.StoreBottomBarHeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.ui.res.stringResource
import com.duggustore.app.R
import com.duggustore.app.data.model.OrderStatus
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.Review
import com.duggustore.app.data.model.UserRole
import com.duggustore.app.data.model.VerificationStatus
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.repository.ProductRepository
import com.duggustore.app.data.repository.ReviewRepository
import com.duggustore.app.ui.components.OnboardingStatusScreen
import com.duggustore.app.ui.components.OrderPlacedDialog
import com.duggustore.app.ui.screens.auth.ForgotPasswordScreen
import com.duggustore.app.ui.screens.auth.LoginScreen
import com.duggustore.app.ui.screens.auth.RegisterScreen
import com.duggustore.app.ui.screens.auth.ResetPasswordScreen
import com.duggustore.app.ui.screens.auth.SplashScreen
import com.duggustore.app.ui.screens.auth.VerifyEmailScreen
import com.duggustore.app.ui.screens.auth.WelcomeScreen
import com.duggustore.app.ui.screens.customer.*
import com.duggustore.app.ui.screens.seller.AddEditProductScreen
import com.duggustore.app.ui.screens.seller.SellerDashboard
import com.duggustore.app.ui.screens.seller.SellerIssuesScreen
import com.duggustore.app.ui.screens.seller.SellerOnboardingScreen
import com.duggustore.app.ui.screens.delivery.DeliveryDashboard
import com.duggustore.app.ui.screens.delivery.DeliveryOnboardingScreen
import com.duggustore.app.ui.screens.delivery.RouteMapScreen
import com.duggustore.app.ui.screens.admin.AdminDashboard
import com.duggustore.app.ui.viewmodel.*

/**
 * How often the seller dashboard re-asks for orders. Nothing pushes to the
 * app, so this interval is the delay between an order being placed and the
 * seller hearing about it — short enough to be useful standing at a counter,
 * long enough not to hammer the API all day.
 */
private const val SELLER_ORDER_POLL_MILLIS = 20_000L

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object ForgotPassword : Screen("forgot_password")
    object Register : Screen("register")
    object VerifyEmail : Screen("verify_email/{email}") {
        fun createRoute(email: String) = "verify_email/$email"
    }
    object CustomerHome : Screen("customer_home")
    object CustomerCart : Screen("customer_cart")
    object CustomerOrders : Screen("customer_orders")
    object CustomerOrderTracking : Screen("customer_order_tracking/{orderId}") {
        fun createRoute(orderId: String) = "customer_order_tracking/$orderId"
    }
    object CustomerCategories : Screen("customer_categories")
    object CustomerNotifications : Screen("customer_notifications")
    object CustomerFavorites : Screen("customer_favorites")
    object CustomerAccount : Screen("customer_account")
    object CustomerSettings : Screen("customer_settings")
    object CustomerAddresses : Screen("customer_addresses")
    object CustomerCheckout : Screen("customer_checkout")
    object CustomerWallet : Screen("customer_wallet")
    object SellerIssues : Screen("seller_issues")
    object ProductDetail : Screen("product_detail/{productId}") {
        fun createRoute(productId: String) = "product_detail/$productId"
    }
    object SellerProductForm : Screen("seller_product_form?productId={productId}&categoryId={categoryId}") {
        /**
         * [categoryId] is how the dashboard's quick-sell tiles open this form
         * already filed — the same screen either way, with the one field the
         * seller has clearly already decided pre-selected. Editing an
         * existing product ignores it: the product's own category wins.
         */
        fun createRoute(productId: String = "", categoryId: String = "") =
            "seller_product_form?productId=$productId&categoryId=$categoryId"
    }
    object SellerDashboard : Screen("seller_dashboard")
    object DeliveryDashboard : Screen("delivery_dashboard")
    object AdminDashboard : Screen("admin_dashboard")
    // kind is "pickup" or "drop" — orderId and kind are both safe as raw path
    // segments (a UUID and a fixed enum string), unlike the free-text address
    // labels the screen needs, which are looked up from view model state
    // instead of round-tripped through the route.
    object RiderRouteMap : Screen("rider_route_map/{orderId}/{kind}") {
        fun createRoute(orderId: String, kind: String) = "rider_route_map/$orderId/$kind"
    }
}

// consumeWindowInsets is still marked experimental in this Compose version.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavGraph(
    navController: NavHostController,
    // Set by MainActivity when the app is opened by tapping a push
    // notification; consumed once so a config change doesn't renavigate.
    pendingNotificationRoute: String? = null,
    onNotificationRouteConsumed: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel(),
    cartViewModel: CartViewModel = viewModel(),
    orderViewModel: OrderViewModel = viewModel(),
    favoriteViewModel: FavoriteViewModel = viewModel(),
    sellerViewModel: SellerViewModel = viewModel(),
    deliveryViewModel: DeliveryViewModel = viewModel(),
    adminViewModel: AdminViewModel = viewModel(),
    addressViewModel: AddressViewModel = viewModel(),
    sellerOnboardingViewModel: SellerOnboardingViewModel = viewModel(),
    deliveryOnboardingViewModel: DeliveryOnboardingViewModel = viewModel()
) {
    val authState by authViewModel.state.collectAsState()
    val homeState by homeViewModel.state.collectAsState()
    val cartState by cartViewModel.state.collectAsState()
    val orderState by orderViewModel.state.collectAsState()
    val favState by favoriteViewModel.state.collectAsState()
    val sellerState by sellerViewModel.state.collectAsState()
    val deliveryState by deliveryViewModel.state.collectAsState()
    val adminState by adminViewModel.state.collectAsState()
    val addressState by addressViewModel.state.collectAsState()
    val sellerOnboardingState by sellerOnboardingViewModel.state.collectAsState()
    val deliveryOnboardingState by deliveryOnboardingViewModel.state.collectAsState()

    // Hold on the splash until the stored session has been checked, otherwise the app
    // shows the login screen for a moment and then jumps to a dashboard.
    if (authState.isRestoringSession) {
        SplashScreen(
            error = authState.error,
            onRetry = { authViewModel.refreshProfile() }
        )
        return
    }

    // A password-reset deep link arrives with a recovery session attached and has to be
    // finished before anything else, so it takes over ahead of the nav graph.
    if (authState.awaitingNewPassword) {
        ResetPasswordScreen(
            onSubmit = { pass, confirm -> authViewModel.updatePassword(pass, confirm) },
            onCancel = { authViewModel.cancelPasswordRecovery() },
            isLoading = authState.isLoading,
            error = authState.error,
            onClearError = { authViewModel.clearError() }
        )
        return
    }

    // Only the very first launch opens on the intro slides; after that a
    // signed-out app goes straight to the login form.
    val context = LocalContext.current
    val hasSeenWelcome = remember { AppPrefs.hasSeenWelcome(context) }
    val startDestination = remember(authState.isLoggedIn, authState.user) {
        if (!authState.isLoggedIn) {
            if (hasSeenWelcome) Screen.Login.route else Screen.Welcome.route
        }
        else {
            when (authState.user?.userRole()) {
                UserRole.SELLER -> Screen.SellerDashboard.route
                UserRole.DELIVERY -> Screen.DeliveryDashboard.route
                UserRole.ADMIN -> Screen.AdminDashboard.route
                else -> Screen.CustomerHome.route
            }
        }
    }

    // Navigate to verify email when signup requires verification
    var hasNavigatedToVerify by remember { mutableStateOf(false) }
    LaunchedEffect(authState.requiresEmailVerification, authState.pendingVerificationEmail) {
        if (authState.requiresEmailVerification && authState.pendingVerificationEmail.isNotEmpty() && !hasNavigatedToVerify) {
            hasNavigatedToVerify = true
            navController.navigate(Screen.VerifyEmail.createRoute(authState.pendingVerificationEmail)) {
                popUpTo(Screen.Register.route) { inclusive = true }
            }
        }
        if (!authState.requiresEmailVerification) {
            hasNavigatedToVerify = false
        }
    }

    // Navigate to role-based dashboard after successful login/signup.
    // popUpTo(0) rather than popUpTo(Login): signing up from the welcome
    // slides never puts a login screen on the stack, and popping to a route
    // that isn't there would leave the sign-up form sitting behind the
    // dashboard for the back button to find.
    var hasNavigatedToDashboard by remember { mutableStateOf(false) }
    LaunchedEffect(authState.isLoggedIn, authState.user) {
        if (authState.isLoggedIn && authState.user != null && !hasNavigatedToDashboard) {
            hasNavigatedToDashboard = true
            val destination = when (authState.user?.userRole()) {
                UserRole.SELLER -> Screen.SellerDashboard.route
                UserRole.DELIVERY -> Screen.DeliveryDashboard.route
                UserRole.ADMIN -> Screen.AdminDashboard.route
                else -> Screen.CustomerHome.route
            }
            navController.navigate(destination) {
                popUpTo(0) { inclusive = true }
            }
        }
        if (!authState.isLoggedIn) {
            hasNavigatedToDashboard = false
        }
    }

    LaunchedEffect(authState.user) {
        authState.user?.let { user ->
            cartViewModel.setCustomer(user.id)
            addressViewModel.setUser(user.id)
            // onNewToken alone only fires when a token is first generated or
            // rotates, not on every app start — a token issued before this
            // login would otherwise never get associated with this user.
            PushNotifications.registerCurrentToken(user.id)
        }
    }

    // Runs after the dashboard-navigation effect above, so this lands on top
    // of it rather than being wiped out by that effect's popUpTo(0).
    LaunchedEffect(pendingNotificationRoute, authState.isLoggedIn) {
        if (pendingNotificationRoute != null && authState.isLoggedIn) {
            navController.navigate(pendingNotificationRoute)
            onNotificationRouteConsumed()
        }
    }

    // A guest's cart never has a customer id to attach to, so addToCart would
    // otherwise just silently do nothing — sign-in is the actual next step,
    // not a dead tap. Every "Add to cart" / quantity-increase action across
    // Home, Categories, Favourites and product detail goes through this.
    fun addToCartOrPromptLogin(product: Product) {
        if (authState.user == null) {
            navController.navigate(Screen.Login.route)
        } else {
            cartViewModel.addToCart(product)
        }
    }

    fun toggleFavoriteOrPromptLogin(product: Product) {
        val user = authState.user
        if (user == null) {
            navController.navigate(Screen.Login.route)
        } else {
            favoriteViewModel.toggleFavorite(user.id, product.id)
        }
    }

    // Sign-out has to clear every user-scoped ViewModel, not just auth. They
    // are all activity-scoped and happily outlive the session: before this
    // existed, the home bell kept showing the previous user's unread badge
    // to a signed-out guest ("notifications without login"), and the wallet
    // banner, cart steppers, favourite hearts and saved-address strip all
    // carried the old account's data into the guest state too.
    fun signOutEverywhere() {
        authViewModel.signOut()
        orderViewModel.clearSession()
        cartViewModel.clearSession()
        addressViewModel.clearSession()
        favoriteViewModel.clearSession()
        sellerOnboardingViewModel.clearSession()
        deliveryOnboardingViewModel.clearSession()
        sellerViewModel.clearSession()
        deliveryViewModel.clearSession()
        adminViewModel.clearSession()
        navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    // Computed once and reused by every screen that needs them (Home,
    // Categories, Favourites, product detail) rather than each one rebuilding
    // its own fresh Map/Set inline — a plain `cartState.cartItems.associate{}`
    // in a composable's parameter list allocates a brand-new instance on
    // every recomposition of this whole function, and since Map/Set aren't
    // structurally skippable, that alone was enough to force every product
    // card's row to recompose whenever anything unrelated changed here.
    val cartQuantities = remember(cartState.cartItems) {
        cartState.cartItems.associate { it.productId to it.quantity }
    }
    val favoriteIds = remember(favState.favorites) {
        favState.favorites.map { it.productId }.toSet()
    }

    // The bar is part of the shell rather than of any one screen, so every
    // top-level destination gets it and the selected item always matches where
    // the user actually is.
    val role = authState.user?.userRole() ?: UserRole.CUSTOMER
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // SellerDashboard and DeliveryDashboard are each one route that branches
    // internally between the onboarding form and the real dashboard — the
    // route name alone can't tell those apart, so an unapproved seller or
    // rider would otherwise still get the Products/Orders tab bar while
    // looking at their KYC form.
    val isOnboardingPending = when (role) {
        UserRole.SELLER -> sellerOnboardingState.seller?.verificationStatus() != VerificationStatus.APPROVED
        UserRole.DELIVERY -> deliveryOnboardingState.partner?.verificationStatus() != VerificationStatus.APPROVED
        else -> false
    }
    val showBar = BottomNav.showsBar(currentRoute, role) && !isOnboardingPending
    // The three dashboards are one screen each with tabs inside them, so the bar
    // drives that index instead of navigating.
    var dashboardTab by rememberSaveable { mutableStateOf(0) }
    // Blinkit-style: tapping Home while already on it jumps the feed to the top
    // instead of doing nothing. A tick, not a boolean — a boolean would miss
    // a second tap before the first scroll finished.
    var homeRetapTick by remember { mutableIntStateOf(0) }

    // The bar sits above the system navigation inset, so the space it occupies is
    // its own height plus that inset. Consuming the same amount stops screens with
    // their own pinned bottoms (the cart's bill sheet) adding the inset a second
    // time underneath it.
    val barSpace = if (showBar) {
        StoreBottomBarHeight +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {

    NavHost(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = barSpace)
            .consumeWindowInsets(PaddingValues(bottom = barSpace)),
        navController = navController,
        startDestination = startDestination,
        // Tabs fade; pushes (PDP, cart, checkout…) opt into appPush* below.
        // Home's bottom-bar tap still goes through popBackStack — pop and
        // forward tab fades match so a tab never looks different depending
        // on which call got it there.
        enterTransition = { appTabEnter() },
        exitTransition = { appTabExit() },
        popEnterTransition = { appTabEnter() },
        popExitTransition = { appTabExit() }
    ) {
        composable(Screen.Welcome.route) {
            // Marked seen on the way out of the slides, whichever exit is
            // taken, so they never reappear on a later launch.
            val leave: (String) -> Unit = { route ->
                AppPrefs.setWelcomeSeen(context)
                navController.navigate(route) {
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                }
            }
            WelcomeScreen(
                onFinish = { leave(Screen.Register.route) },
                onLogIn = { leave(Screen.Login.route) }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onForgotPassword = {
                    authViewModel.resetPasswordResetState()
                    navController.navigate(Screen.ForgotPassword.route)
                },
                // Browsing doesn't touch authState at all — the customer home
                // route already treats a null user as a guest (no name in the
                // greeting, favourites/orders never loaded). The bottom bar's
                // Favourites, Account and Cart still send a guest back here
                // when they actually need one.
                onSkip = {
                    navController.navigate(Screen.CustomerHome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                isLoading = authState.isLoading,
                error = authState.error,
                onLogin = { email, password -> authViewModel.signIn(email, password) },
                onClearError = { authViewModel.clearError() }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                // Navigation to the right dashboard after a successful signup is
                // already handled by the hasNavigatedToDashboard effect above,
                // which watches authState.isLoggedIn/user app-wide — a second,
                // screen-local success callback here never actually ran.
                //
                // Pops back to an existing login screen, and only navigates to
                // one when there isn't a login underneath — coming here from the
                // welcome slides, sign-up is the bottom of the stack, so a plain
                // popBackStack() would empty it.
                onNavigateToLogin = {
                    if (!navController.popBackStack(Screen.Login.route, false)) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Register.route) { inclusive = true }
                        }
                    }
                },
                isLoading = authState.isLoading,
                error = authState.error,
                successMessage = authState.successMessage,
                onRegister = { email, pass, name, phone, role, referralCode ->
                    authViewModel.signUp(email, pass, name, phone, role, referralCode)
                },
                onClearError = { authViewModel.clearError() },
                onClearSuccess = { authViewModel.clearSuccess() }
            )
        }

        composable(
            Screen.VerifyEmail.route,
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: authState.pendingVerificationEmail

            VerifyEmailScreen(
                email = email,
                onVerifyCode = { code -> authViewModel.verifyEmailCode(email, code) },
                onResendEmail = { authViewModel.resendVerificationEmail(email) },
                onBackToLogin = {
                    authViewModel.resetVerificationState()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                isLoading = authState.isLoading,
                error = authState.error,
                verificationResent = authState.verificationResent,
                onClearError = { authViewModel.clearError() }
            )
        }

        composable(
            Screen.CustomerNotifications.route,
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) {
            // One source: the notifications table. Four order triggers write
            // rows into it, and a trigger on that insert is what sends the
            // push — so a row is the notification and the push is its
            // delivery. Read state is a column on that row, which is why it
            // survives a reinstall and agrees across a user's devices.
            NotificationsScreen(
                notifications = orderState.dbNotifications,
                readIds = orderState.readNotificationIds,
                onNotificationClick = { notification ->
                    orderViewModel.markNotificationRead(notification.id)
                    if (notification.orderId.isNotBlank()) {
                        navController.navigate(
                            Screen.CustomerOrderTracking.createRoute(notification.orderId)
                        )
                    } else {
                        navController.navigate(Screen.CustomerOrders.route)
                    }
                },
                onMarkAllRead = {
                    authState.user?.let { orderViewModel.markAllNotificationsRead(it.id) }
                },
                onBack = { navController.popBackStack() }
            )
            // Re-read on open so a push that arrived while the app was in the
            // background is on screen. Orders and their items are no longer
            // fetched here: they only ever fed the derived list, and pulling
            // every order's items was a query per order for text the rows
            // already carry, written by the trigger that created them.
            LaunchedEffect(authState.user) {
                authState.user?.let { orderViewModel.loadNotifications(it.id) }
            }
        }

        composable(Screen.CustomerCategories.route) {
            CategoriesScreen(
                categories = homeState.categories,
                products = homeState.products,
                cartQuantities = cartQuantities,
                favoriteIds = favoriteIds,
                onAddToCart = ::addToCartOrPromptLogin,
                onIncrease = ::addToCartOrPromptLogin,
                onDecrease = { product ->
                    cartState.cartItems.firstOrNull { it.productId == product.id }?.let { item ->
                        cartViewModel.updateQuantity(item.id, item.quantity - 1)
                    }
                },
                onToggleFavorite = ::toggleFavoriteOrPromptLogin,
                onProductClick = { navController.navigate(Screen.ProductDetail.createRoute(it.id)) }
            )
        }

        composable(Screen.CustomerHome.route) {
            HomeScreen(
                categories = homeState.categories,
                selectedCategoryId = homeState.selectedCategoryId,
                searchQuery = homeState.searchQuery,
                onSearchQueryChange = { homeViewModel.search(it) },
                onCategorySelected = { homeViewModel.selectCategory(it) },
                onQuickShopOpen = { homeViewModel.openQuickShop(it) },
                quickShopCategoryId = homeState.quickShopCategoryId,
                quickShopProducts = homeState.quickShopProducts,
                isQuickShopLoading = homeState.isQuickShopLoading,
                onQuickShopDismiss = { homeViewModel.closeQuickShop() },
                onAddToCart = ::addToCartOrPromptLogin,
                onProductClick = { navController.navigate(Screen.ProductDetail.createRoute(it.id)) },
                deliveryAddress = addressState.defaultAddress?.fullAddress
                    ?: stringResource(R.string.home_set_address),
                // Lets a card show a stepper instead of "Add to cart" once the
                // product is already in the cart, as in the design.
                cartQuantities = cartQuantities,
                favoriteIds = favoriteIds,
                onIncrease = ::addToCartOrPromptLogin,
                onDecrease = { product ->
                    cartState.cartItems.firstOrNull { it.productId == product.id }?.let { item ->
                        cartViewModel.updateQuantity(item.id, item.quantity - 1)
                    }
                },
                onToggleFavorite = ::toggleFavoriteOrPromptLogin,
                // The addresses screen saves to the signed-in user's rows; a
                // guest tapping through from the location sheet would land on
                // a form whose every save silently did nothing.
                onAddressClick = {
                    if (authState.user == null) navController.navigate(Screen.Login.route)
                    else navController.navigate(Screen.CustomerAddresses.route)
                },
                // Unread rows from the server once they are in, and nothing at
                // all before that. It used to fall back to counting in-flight
                // Unread rows from the table, and nothing before the first read
                // lands — "not asked yet" must not look like "nothing unread",
                // or the badge shows a number it then has to take back.
                // The user check is belt-and-braces: a signed-out guest must
                // never see a badge even if stale state survives somewhere.
                notificationCount = if (authState.user != null && orderState.notificationsLoaded)
                    orderState.dbNotifications.count { it.id !in orderState.readNotificationIds }
                else
                    0,
                // Notifications are per-user rows, so for a guest the screen
                // would just be an empty list — take them to login instead,
                // the same pattern as add-to-cart.
                onNotificationsClick = {
                    if (authState.user == null) navController.navigate(Screen.Login.route)
                    else navController.navigate(Screen.CustomerNotifications.route)
                },
                savedAddresses = addressState.addresses,
                onSelectAddress = { addressViewModel.setDefault(it.id) },
                // A detected address is only useful if it survives the session,
                // so picking it saves it as the new default rather than holding
                // it in memory until the next launch. Reuses the existing
                // "Current location" row if one is already saved — every tap
                // used to insert a fresh one instead of updating it, so the
                // address list filled up with duplicates of the same spot.
                // A guest has no row to save to, which used to make this a
                // silent no-op: the sheet closed and nothing changed.
                onSaveDetectedAddress = { detected, lat, lng ->
                    if (authState.user == null) {
                        navController.navigate(Screen.Login.route)
                    } else {
                        val existing = addressState.addresses.firstOrNull { it.label == "Current location" }
                        addressViewModel.saveAddress(
                            label = "Current location",
                            fullAddress = detected,
                            isDefault = true,
                            existingId = existing?.id ?: "",
                            latitude = lat,
                            longitude = lng
                        )
                    }
                },
                offers = homeState.offers,
                // The card carries a real coupon code, so tapping it puts the
                // customer where they can spend it.
                onOfferClick = { navController.navigate(Screen.CustomerCart.route) },
                isLoading = homeState.isLoading,
                error = homeState.error,
                onRefresh = { homeViewModel.loadData() },
                onRetry = { homeViewModel.loadData() },
                feedProducts = homeState.feedProducts,
                hasMoreFeed = homeState.hasMoreFeed,
                isLoadingMoreFeed = homeState.isLoadingMoreFeed,
                onLoadMoreFeed = { homeViewModel.loadMoreFeed() },
                allProducts = homeState.products,
                walletBalance = cartState.walletBalance,
                referralCode = authState.user?.referralCode.orEmpty(),
                onWalletBannerClick = { navController.navigate(Screen.CustomerWallet.route) },
                campaigns = homeState.campaigns,
                sponsoredSlots = homeState.sponsoredSlots,
                homeSections = homeState.homeSections,
                scrollToTopTick = homeRetapTick
            )

            LaunchedEffect(authState.user) {
                authState.user?.let { orderViewModel.loadCustomerOrders(it.id) }
                authState.user?.let { favoriteViewModel.loadFavorites(it.id) }
                // The bell lives on this screen, so its rows have to be
                // fetched here rather than only when the notifications screen
                // opens — otherwise the badge has nothing to count until you
                // have already been where it was pointing you.
                authState.user?.let { orderViewModel.loadNotifications(it.id) }
                cartViewModel.loadCart()
            }
            // Not re-triggered here: homeViewModel's own init{} already loads
            // once for the whole session (it's created at AppNavGraph's
            // scope, not per-visit). A LaunchedEffect(Unit) here reruns every
            // time this composable re-enters — i.e. every tab switch back to
            // Home — which refetched the whole catalogue and popped the
            // pull-to-refresh spinner with no actual pull behind it, on
            // every single Home tap.
        }

        composable(
            Screen.CustomerCart.route,
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) {
            CartScreen(
                cartItems = cartState.cartItems,
                subtotal = cartState.subtotal,
                deliveryFee = cartState.deliveryFee,
                total = cartState.total,
                savings = cartState.savings,
                couponApplied = cartState.couponApplied,
                couponDiscount = cartState.couponDiscount,
                couponError = cartState.couponError,
                belowMinimumOrder = cartState.isBelowMinimumOrder,
                minOrderValue = CartState.MIN_ORDER_VALUE,
                isLoading = cartState.isLoading,
                // The screen passes the quantity it wants, already adjusted.
                onIncrementQuantity = { itemId, qty -> cartViewModel.updateQuantity(itemId, qty) },
                onDecrementQuantity = { itemId, qty -> cartViewModel.updateQuantity(itemId, qty) },
                onRemoveItem = { cartViewModel.removeItem(it) },
                onApplyCoupon = { cartViewModel.applyCoupon(it) },
                onPlaceOrder = { navController.navigate(Screen.CustomerCheckout.route) },
                addresses = addressState.addresses,
                walletBalance = cartState.walletBalance,
                orderPlaced = cartState.orderPlaced,
                onQuickPlaceOrder = { address, lat, lng, walletAmount ->
                    cartViewModel.placeOrder(address, lat, lng, walletAmount)
                },
                onManageAddresses = { navController.navigate(Screen.CustomerAddresses.route) },
                onBack = { navController.popBackStack() },
                error = cartState.error,
                onRefresh = { cartViewModel.loadCart() },
                onRetry = { cartViewModel.loadCart() }
            )

            // The quick sheet places an order without visiting the checkout
            // route, so the addresses it offers have to be loaded here too —
            // otherwise the sheet opens with an empty "deliver to" on a cold
            // start straight into the bag.
            LaunchedEffect(Unit) { addressViewModel.loadAddresses() }

            // Not reloaded on every visit here — Home's own LaunchedEffect
            // already loads the cart once per login, and every in-app cart
            // action (add/remove/update) already updates this state itself
            // rather than relying on a re-fetch to see its own result.
            if (cartState.orderPlaced) {
                OrderPlacedDialog(
                    onTrackOrder = {
                        cartViewModel.resetOrderPlaced()
                        navController.navigate(Screen.CustomerOrders.route)
                    },
                    onContinueShopping = { cartViewModel.resetOrderPlaced() }
                )
            }
        }

        composable(
            Screen.CustomerOrders.route,
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) {
            val remindersContext = LocalContext.current
            var ordersSearchQuery by rememberSaveable { mutableStateOf("") }
            var ordersSelectedStatus by rememberSaveable { mutableStateOf("All") }
            OrderListScreen(
                orders = orderState.customerOrders,
                itemsByOrderId = orderState.orderItemsByOrderId,
                onOrderClick = { orderId ->
                    navController.navigate(Screen.CustomerOrderTracking.createRoute(orderId))
                },
                onBack = { navController.popBackStack() },
                dueReminderOrderIds = remember(orderState.customerOrders) {
                    AppPrefs.dueReorderReminders(remindersContext).toSet()
                },
                isLoading = orderState.isLoading,
                error = orderState.error,
                onRefresh = { authState.user?.let { orderViewModel.loadCustomerOrders(it.id) } },
                onRetry = { authState.user?.let { orderViewModel.loadCustomerOrders(it.id) } },
                searchQuery = ordersSearchQuery,
                onSearchQueryChange = { ordersSearchQuery = it },
                selectedStatus = ordersSelectedStatus,
                onStatusSelected = { ordersSelectedStatus = it }
            )

            // Not reloaded on entry here — Home's own LaunchedEffect already
            // loads it once per login, and the poll below keeps it fresh for
            // as long as this screen stays open.
            // The list used to show only an order number and total — no clue
            // what was actually ordered until tapping in. Loading each order's
            // items as soon as the list is known lets every card show the
            // product name and photo straight away.
            LaunchedEffect(orderState.customerOrders) {
                orderState.customerOrders.forEach { orderViewModel.loadOrderItems(it.id) }
            }

            // Otherwise a seller accepting or a rider updating an order while
            // this list is on screen never shows up until the customer backs
            // out and re-enters it.
            LaunchedEffect(authState.user?.id) {
                val userId = authState.user?.id ?: return@LaunchedEffect
                while (true) {
                    delay(15_000L)
                    orderViewModel.loadCustomerOrders(userId)
                }
            }
        }

        composable(
            Screen.CustomerOrderTracking.route,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
            val order = orderState.customerOrders.find { it.id == orderId }
            val reminderContext = LocalContext.current
            var hasReorderReminder by remember(orderId) {
                mutableStateOf(AppPrefs.hasReorderReminder(reminderContext, orderId))
            }

            LaunchedEffect(orderId) {
                if (order == null) orderViewModel.loadOrder(orderId)
            }

            if (order == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val it = order
                val riderPosition = rememberRiderPosition(
                    tracking = orderState.tracking,
                    deliveryAddress = it.deliveryAddress
                )

                OrderTrackingDetailScreen(
                    order = it,
                    onCancelOrder = { orderViewModel.cancelOrder(orderId) },
                    onBack = { navController.popBackStack() },
                    tracking = orderState.tracking,
                    riderDistanceMetres = riderPosition.distanceMetres,
                    riderFixAgeMinutes = riderPosition.ageMinutes,
                    items = orderState.orderItemsByOrderId[orderId] ?: emptyList(),
                    myReviews = orderState.myReviewsByOrderId[orderId] ?: emptyMap(),
                    onSubmitReview = { productId, rating, comment ->
                        authState.user?.let { user ->
                            orderViewModel.submitReview(user.id, orderId, productId, rating, comment)
                        }
                    },
                    onReorder = {
                        cartViewModel.reorderItems(orderState.orderItemsByOrderId[orderId] ?: emptyList())
                        navController.navigate(Screen.CustomerCart.route)
                    },
                    myIssues = orderState.myIssuesByOrderId[orderId] ?: emptyList(),
                    onReportIssue = { reason, description ->
                        authState.user?.let { user ->
                            orderViewModel.reportIssue(orderId, null, user.id, reason, description)
                        }
                    },
                    hasReorderReminder = hasReorderReminder,
                    onSetReorderReminder = {
                        AppPrefs.setReorderReminder(reminderContext, orderId, days = 7)
                        hasReorderReminder = true
                    }
                )

                LaunchedEffect(orderId) { orderViewModel.loadOrderItems(orderId) }

                LaunchedEffect(orderId, it.status) {
                    if (it.status == OrderStatus.DELIVERED.value) {
                        authState.user?.let { user ->
                            orderViewModel.loadMyReviews(user.id, orderId)
                            orderViewModel.loadMyIssues(user.id, orderId)
                        }
                    }
                }

                // Polled only while this screen is on top and the order is
                // actually on the road; the effect is cancelled when either
                // stops being true.
                LaunchedEffect(orderId, it.status) {
                    if (it.status != OrderStatus.OUT_FOR_DELIVERY.value) {
                        orderViewModel.clearTracking()
                        return@LaunchedEffect
                    }
                    while (true) {
                        orderViewModel.loadTracking(orderId)
                        delay(15_000L)
                    }
                }

                // The order itself was only fetched once, when the list
                // screen loaded — without this, a status change the seller
                // or rider makes while the customer is sitting on this exact
                // screen never appears until they back out and re-enter it.
                // Stops once the order reaches a state that will not change
                // again.
                LaunchedEffect(orderId, it.status) {
                    val terminal = it.status == OrderStatus.DELIVERED.value ||
                        it.status == OrderStatus.CANCELLED.value
                    if (terminal) return@LaunchedEffect
                    val userId = authState.user?.id ?: return@LaunchedEffect
                    while (true) {
                        delay(10_000L)
                        orderViewModel.loadCustomerOrders(userId)
                    }
                }
            }
        }

        composable(Screen.CustomerFavorites.route) {
            FavoritesScreen(
                favoriteProducts = favState.favoriteProducts,
                onAddToCart = ::addToCartOrPromptLogin,
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate(Screen.ProductDetail.createRoute(it.id)) },
                cartQuantities = cartQuantities,
                onIncrease = ::addToCartOrPromptLogin,
                onDecrease = { product ->
                    cartState.cartItems.firstOrNull { it.productId == product.id }?.let { item ->
                        cartViewModel.updateQuantity(item.id, item.quantity - 1)
                    }
                },
                onRemoveFavorite = { product ->
                    authState.user?.let { favoriteViewModel.toggleFavorite(it.id, product.id) }
                },
                isLoading = favState.isLoading
            )

            // Not reloaded on entry here — Home's own LaunchedEffect already
            // loads it once per login, and toggling a favourite already
            // updates this state itself rather than needing a re-fetch.
        }

        composable(Screen.CustomerAccount.route) {
            AccountScreen(
                user = authState.user,
                onOrdersClick = { navController.navigate(Screen.CustomerOrders.route) },
                onFavoritesClick = { navController.navigate(Screen.CustomerFavorites.route) },
                onAddressesClick = { navController.navigate(Screen.CustomerAddresses.route) },
                onWalletClick = { navController.navigate(Screen.CustomerWallet.route) },
                // Same guard as the home bell: notifications are per-user
                // rows, so a guest goes to sign-in rather than an empty list
                // (or worse, whatever stale rows are left).
                onNotificationsClick = {
                    if (authState.user == null) navController.navigate(Screen.Login.route)
                    else navController.navigate(Screen.CustomerNotifications.route)
                },
                onSettingsClick = { navController.navigate(Screen.CustomerSettings.route) },
                onSignOut = { signOutEverywhere() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.CustomerSettings.route,
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) {
            SettingsScreen(
                user = authState.user,
                isSaving = authState.isLoading,
                error = authState.error,
                passwordUpdated = authState.passwordUpdated,
                onSaveProfile = { name, phone -> authViewModel.updateProfile(name, phone) },
                onChangePassword = { newPassword, confirm -> authViewModel.updatePassword(newPassword, confirm) },
                onUploadAvatar = { bytes, mimeType -> authViewModel.uploadAvatar(bytes, mimeType) },
                onClearError = { authViewModel.clearError() },
                onClearPasswordUpdated = { authViewModel.clearPasswordUpdated() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.CustomerWallet.route,
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) {
            WalletScreen(
                transactions = orderState.walletTransactions,
                referralCode = authState.user?.referralCode ?: "",
                onBack = { navController.popBackStack() },
                isLoading = orderState.isWalletLoading
            )
            LaunchedEffect(Unit) {
                authState.user?.let { orderViewModel.loadWallet(it.id) }
            }
        }

        composable(
            Screen.SellerIssues.route,
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) {
            SellerIssuesScreen(
                issues = orderState.issuesForReview,
                onResolve = { issueId, approve, refundAmount ->
                    orderViewModel.resolveIssue(issueId, approve, refundAmount)
                },
                onBack = { navController.popBackStack() }
            )
            LaunchedEffect(Unit) { orderViewModel.loadIssuesForReview() }
        }

        composable(Screen.SellerDashboard.route) {
            LaunchedEffect(authState.user) {
                authState.user?.let { sellerOnboardingViewModel.load(it.id) }
            }

            val seller = sellerOnboardingState.seller
            val sellerStatus = seller?.verificationStatus() ?: VerificationStatus.PENDING_VERIFICATION
            val onOnboardingSignOut: () -> Unit = { signOutEverywhere() }

            when {
                !sellerOnboardingState.hasLoaded -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                sellerStatus == VerificationStatus.APPROVED -> {
                    val announcer = rememberOrderAnnouncer()
                    var voiceAlertsEnabled by remember { mutableStateOf(AppPrefs.isOrderAlertEnabled(context)) }

                    // Nothing pushes to the app, so an order only turns up when
                    // it is asked for — and an announcement nobody heard because
                    // the list was an hour stale is no announcement at all.
                    LaunchedEffect(authState.user) {
                        val user = authState.user ?: return@LaunchedEffect
                        orderViewModel.loadIssuesForReview()
                        while (true) {
                            sellerViewModel.loadSellerData(user.id)
                            delay(SELLER_ORDER_POLL_MILLIS)
                        }
                    }

                    LaunchedEffect(sellerState.newOrderAlerts) {
                        val arrived = sellerState.newOrderAlerts
                        if (arrived.isEmpty()) return@LaunchedEffect
                        if (voiceAlertsEnabled) {
                            arrived.forEach { order ->
                                announcer.announce(
                                    context.getString(
                                        R.string.seller_order_announcement,
                                        order.totalAmount.roundToInt()
                                    )
                                )
                            }
                        }
                        sellerViewModel.consumeOrderAlerts()
                    }

                    // Items were only ever fetched once a card was expanded, so
                    // an order's own list showed nothing about what was in it —
                    // an opaque order number instead of the product, and no
                    // photo — until the seller tapped in. Loading them as soon
                    // as the orders themselves are known lets the card show a
                    // real summary straight away; loadOrderItems already skips
                    // any order it has already fetched.
                    LaunchedEffect(sellerState.orders) {
                        sellerState.orders.forEach { orderViewModel.loadOrderItems(it.id) }
                    }

                    SellerDashboard(
                        selectedTab = dashboardTab,
                        products = sellerState.products,
                        orders = sellerState.orders,
                        isLoading = sellerState.isLoading,
                        onRefreshOrders = { authState.user?.let { sellerViewModel.loadSellerData(it.id) } },
                        totalRevenue = sellerState.totalRevenue,
                        totalOrders = sellerState.totalOrders,
                        onAddProduct = { navController.navigate(Screen.SellerProductForm.createRoute()) },
                        onEditProduct = { navController.navigate(Screen.SellerProductForm.createRoute(it)) },
                        // Quick sell: the same form, opened from a category on
                        // the dashboard so the listing starts one decision in.
                        // Every category, not the stocked-only browse list, for
                        // the same reason the form itself gets them all — a
                        // seller has to be able to file into an empty one.
                        categories = homeState.allCategories,
                        onQuickSell = { categoryId ->
                            navController.navigate(
                                Screen.SellerProductForm.createRoute(categoryId = categoryId)
                            )
                        },
                        onDeleteProduct = { sellerViewModel.deleteProduct(it, authState.user?.id ?: "") },
                        onUpdateOrderStatus = { orderId, status ->
                            sellerViewModel.updateOrderStatus(orderId, status, authState.user?.id ?: "")
                        },
                        orderItemsByOrderId = orderState.orderItemsByOrderId,
                        onExpandOrderItems = { orderViewModel.loadOrderItems(it) },
                        hasStoreLocation = authState.user?.storeLatitude != null,
                        onSaveStoreLocation = { address, lat, lng ->
                            authViewModel.updateStoreLocation(address, lat, lng)
                        },
                        openIssuesCount = orderState.issuesForReview.count { it.status == "open" },
                        onIssuesClick = { navController.navigate(Screen.SellerIssues.route) },
                        voiceAlertsEnabled = voiceAlertsEnabled,
                        onToggleVoiceAlerts = {
                            voiceAlertsEnabled = !voiceAlertsEnabled
                            AppPrefs.setOrderAlertEnabled(context, voiceAlertsEnabled)
                        },
                        sponsoredSlots = sellerState.sponsoredSlots,
                        onRequestSponsoredSlot = { productId, days, note ->
                            authState.user?.let { sellerViewModel.requestSponsoredSlot(it.id, productId, days, note) }
                        },
                        quoteSlotFee = { days -> sellerViewModel.quoteSlotFee(days) },
                        isRequestingSlot = sellerState.isRequestingSlot,
                        slotRequestError = sellerState.slotRequestError,
                        onClearSlotRequestError = { sellerViewModel.clearSlotRequestError() },
                        onSignOut = onOnboardingSignOut
                    )
                }
                sellerStatus == VerificationStatus.UNDER_REVIEW || sellerStatus == VerificationStatus.SUSPENDED -> {
                    OnboardingStatusScreen(
                        status = sellerStatus,
                        rejectionReason = seller?.rejectionReason,
                        onSignOut = onOnboardingSignOut
                    )
                }
                else -> {
                    SellerOnboardingScreen(
                        userId = authState.user?.id ?: "",
                        prefillEmail = SessionManager.getEmail().orEmpty(),
                        prefillPhone = authState.user?.phone.orEmpty(),
                        existing = seller,
                        documents = sellerOnboardingState.documents,
                        isSaving = sellerOnboardingState.isSaving,
                        isSubmitting = sellerOnboardingState.isSubmitting,
                        uploadingDocType = sellerOnboardingState.uploadingDocType,
                        error = sellerOnboardingState.error,
                        onUploadDocument = { docType, bytes, contentType ->
                            authState.user?.let { sellerOnboardingViewModel.uploadDocument(it.id, docType, bytes, contentType) }
                        },
                        onSave = { sellerOnboardingViewModel.save(it) },
                        onSubmit = { authState.user?.let { sellerOnboardingViewModel.submit(it.id) } },
                        onClearError = { sellerOnboardingViewModel.clearError() },
                        onLocationPicked = { address, lat, lng ->
                            authViewModel.updateStoreLocation(address, lat, lng)
                        },
                        onSignOut = onOnboardingSignOut
                    )
                }
            }
        }

        composable(Screen.DeliveryDashboard.route) {
            LaunchedEffect(authState.user) {
                authState.user?.let { deliveryOnboardingViewModel.load(it.id) }
            }

            val partner = deliveryOnboardingState.partner
            val partnerStatus = partner?.verificationStatus() ?: VerificationStatus.PENDING_VERIFICATION
            val onOnboardingSignOut: () -> Unit = { signOutEverywhere() }

            when {
                !deliveryOnboardingState.hasLoaded -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                partnerStatus == VerificationStatus.APPROVED -> {
                    // Same reasoning as the seller dashboard: fetch each visible
                    // order's items up front instead of only once expanded.
                    LaunchedEffect(deliveryState.availableOrders, deliveryState.activeOrders, deliveryState.completedOrders) {
                        (deliveryState.availableOrders + deliveryState.activeOrders + deliveryState.completedOrders)
                            .forEach { orderViewModel.loadOrderItems(it.id) }
                    }

                    DeliveryDashboard(
                        selectedTab = dashboardTab,
                        availableOrders = deliveryState.availableOrders,
                        activeOrders = deliveryState.activeOrders,
                        completedOrders = deliveryState.completedOrders,
                        totalEarnings = deliveryState.totalEarnings,
                        totalDeliveries = deliveryState.totalDeliveries,
                        onMarkDelivered = { deliveryViewModel.markDelivered(it, authState.user?.id ?: "") },
                        onClaimOrder = { orderId ->
                            deliveryViewModel.claimOrder(orderId, authState.user?.id ?: "")
                        },
                        claimError = deliveryState.claimError,
                        onDismissClaimError = { deliveryViewModel.clearClaimError() },
                        onNavigateToPickup = { navController.navigate(Screen.RiderRouteMap.createRoute(it.id, "pickup")) },
                        onNavigateToDrop = { navController.navigate(Screen.RiderRouteMap.createRoute(it.id, "drop")) },
                        onSignOut = onOnboardingSignOut,
                        sharingLocation = deliveryState.sharingLocation,
                        sharingError = deliveryState.sharingError,
                        onSharingChange = { deliveryViewModel.setSharingLocation(it) },
                        orderItemsByOrderId = orderState.orderItemsByOrderId,
                        onExpandOrderItems = { orderViewModel.loadOrderItems(it) },
                        isOnline = authState.user?.isOnline ?: false,
                        onToggleOnline = { authViewModel.setOnline(it) },
                        error = deliveryState.error,
                        onDismissError = { deliveryViewModel.clearError() },
                        isLoading = deliveryState.isLoading,
                        onRefreshAvailable = { deliveryViewModel.loadAvailableOrders() },
                        onRefreshActive = { authState.user?.let { deliveryViewModel.loadDeliveryData(it.id) } },
                        dailyEarnings = deliveryState.dailyEarnings
                    )

                    // Runs only while the dashboard is on screen and the rider has the
                    // switch on; leaving the screen removes the location listener.
                    RiderLocationPublisher(
                        enabled = deliveryState.sharingLocation,
                        onFix = { location ->
                            authState.user?.let { user ->
                                deliveryViewModel.publishLocation(
                                    deliveryId = user.id,
                                    latitude = location.latitude,
                                    longitude = location.longitude
                                )
                            }
                        }
                    )

                    LaunchedEffect(authState.user) {
                        authState.user?.let { deliveryViewModel.loadDeliveryData(it.id) }
                    }

                    // Polled only while the Available tab is actually showing and the
                    // rider is online, same pattern as the customer's rider-position
                    // poll: other riders can claim a pool order at any time, so a
                    // one-time load would go stale.
                    LaunchedEffect(dashboardTab, authState.user?.isOnline) {
                        if (dashboardTab != 0 || authState.user?.isOnline != true) return@LaunchedEffect
                        while (true) {
                            deliveryViewModel.loadAvailableOrders()
                            delay(15_000L)
                        }
                    }
                }
                partnerStatus == VerificationStatus.UNDER_REVIEW || partnerStatus == VerificationStatus.SUSPENDED -> {
                    OnboardingStatusScreen(
                        status = partnerStatus,
                        rejectionReason = partner?.rejectionReason,
                        onSignOut = onOnboardingSignOut
                    )
                }
                else -> {
                    DeliveryOnboardingScreen(
                        userId = authState.user?.id ?: "",
                        prefillEmail = SessionManager.getEmail().orEmpty(),
                        prefillPhone = authState.user?.phone.orEmpty(),
                        existing = partner,
                        documents = deliveryOnboardingState.documents,
                        isSaving = deliveryOnboardingState.isSaving,
                        isSubmitting = deliveryOnboardingState.isSubmitting,
                        uploadingDocType = deliveryOnboardingState.uploadingDocType,
                        error = deliveryOnboardingState.error,
                        onUploadDocument = { docType, bytes, contentType ->
                            authState.user?.let { deliveryOnboardingViewModel.uploadDocument(it.id, docType, bytes, contentType) }
                        },
                        onSave = { deliveryOnboardingViewModel.save(it) },
                        onSubmit = { authState.user?.let { deliveryOnboardingViewModel.submit(it.id) } },
                        onClearError = { deliveryOnboardingViewModel.clearError() },
                        onSignOut = onOnboardingSignOut
                    )
                }
            }
        }

        composable(
            Screen.RiderRouteMap.route,
            arguments = listOf(
                navArgument("orderId") { type = NavType.StringType },
                navArgument("kind") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
            val kind = backStackEntry.arguments?.getString("kind") ?: "pickup"
            val order = (deliveryState.activeOrders + deliveryState.availableOrders)
                .firstOrNull { it.id == orderId }

            // Show an error card instead of silently going back when coordinates
            // are missing — the rider tapped Navigate and deserves to know why
            // the map can't open rather than just finding themselves on the
            // previous screen with no explanation.
            var noCoordinatesError by remember { mutableStateOf<String?>(null) }

            if (order == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else if (kind == "pickup") {
                val lat = order.seller?.storeLatitude
                val lng = order.seller?.storeLongitude
                if (lat == null || lng == null) {
                    noCoordinatesError = "Seller hasn't set a store location yet. Contact them to add it."
                } else {
                    RouteMapScreen(
                        title = "Navigate to pickup",
                        destinationLabel = order.seller.storeAddress?.ifBlank { "Store" } ?: "Store",
                        destinationLat = lat,
                        destinationLng = lng,
                        onBack = { navController.popBackStack() }
                    )
                }
            } else {
                val lat = order.deliveryLatitude
                val lng = order.deliveryLongitude
                if (lat == null || lng == null) {
                    noCoordinatesError = "Delivery address has no GPS coordinates. The customer typed it manually — use the address text to navigate."
                } else {
                    RouteMapScreen(
                        title = "Navigate to drop",
                        destinationLabel = order.deliveryAddress.ifBlank { "Customer" },
                        destinationLat = lat,
                        destinationLng = lng,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            noCoordinatesError?.let { message ->
                AlertDialog(
                    onDismissRequest = { navController.popBackStack() },
                    title = { Text("Location not available") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text("Go back")
                        }
                    }
                )
            }
        }

        composable(Screen.AdminDashboard.route) {
            AdminDashboard(
                selectedTab = dashboardTab,
                users = adminState.users,
                orders = adminState.orders,
                products = adminState.products,
                totalUsers = adminState.totalUsers,
                totalOrders = adminState.totalOrders,
                totalRevenue = adminState.totalRevenue,
                totalDeliveries = adminState.totalDeliveries,
                onUpdateUserRole = { userId, role -> adminViewModel.updateUserRole(userId, role) },
                allSellers = sellerOnboardingState.allSellers,
                sellerDocuments = sellerOnboardingState.reviewDocuments,
                sellerDocumentUrls = sellerOnboardingState.reviewDocumentUrls,
                loadingSellerDocsFor = sellerOnboardingState.loadingReviewDocsFor,
                onLoadSellerDocuments = { sellerOnboardingViewModel.loadReviewDocuments(it) },
                onReviewSeller = { sellerId, approve, reason -> sellerOnboardingViewModel.review(sellerId, approve, reason) },
                reviewingSellerId = sellerOnboardingState.reviewingId,
                sellerReviewError = sellerOnboardingState.reviewError,
                onClearSellerReviewError = { sellerOnboardingViewModel.clearReviewError() },
                onBlockSeller = { sellerOnboardingViewModel.blockSeller(it) },
                onUnblockSeller = { sellerOnboardingViewModel.unblockSeller(it) },
                onDeleteSeller = { sellerOnboardingViewModel.deleteSeller(it) },
                // Both of these change the profiles table too — purge deletes the
                // account outright, promote creates the seller record — so the
                // admin's user list has to be reloaded alongside the seller list,
                // or the stale row keeps offering actions against an id that is
                // already gone.
                onPurgeSeller = {
                    sellerOnboardingViewModel.purgeSeller(it) { adminViewModel.loadDashboard() }
                },
                onPromoteToSeller = {
                    sellerOnboardingViewModel.promoteToSeller(it) { adminViewModel.loadDashboard() }
                },
                managingSellerId = sellerOnboardingState.managingId,
                sellerManageError = sellerOnboardingState.manageError,
                onClearSellerManageError = { sellerOnboardingViewModel.clearManageError() },
                allPartners = deliveryOnboardingState.allPartners,
                partnerDocuments = deliveryOnboardingState.reviewDocuments,
                partnerDocumentUrls = deliveryOnboardingState.reviewDocumentUrls,
                loadingPartnerDocsFor = deliveryOnboardingState.loadingReviewDocsFor,
                onLoadPartnerDocuments = { deliveryOnboardingViewModel.loadReviewDocuments(it) },
                onReviewPartner = { partnerId, approve, reason -> deliveryOnboardingViewModel.review(partnerId, approve, reason) },
                reviewingPartnerId = deliveryOnboardingState.reviewingId,
                partnerReviewError = deliveryOnboardingState.reviewError,
                onClearPartnerReviewError = { deliveryOnboardingViewModel.clearReviewError() },
                issues = orderState.issuesForReview,
                onResolveIssue = { issueId, approve, refundAmount ->
                    orderViewModel.resolveIssue(issueId, approve, refundAmount)
                },
                categories = adminState.categories,
                coupons = adminState.coupons,
                campaigns = adminState.campaigns,
                isSavingCatalog = adminState.isSavingCatalog,
                catalogError = adminState.catalogError,
                onClearCatalogError = { adminViewModel.clearCatalogError() },
                onToggleProductActive = { adminViewModel.toggleProductActive(it) },
                onSaveCategory = { adminViewModel.saveCategory(it) },
                onToggleCategoryActive = { adminViewModel.toggleCategoryActive(it) },
                onDeleteCategory = { adminViewModel.deleteCategory(it) },
                onSaveCoupon = { adminViewModel.saveCoupon(it) },
                onToggleCouponActive = { adminViewModel.toggleCouponActive(it) },
                onDeleteCoupon = { adminViewModel.deleteCoupon(it) },
                onSaveCampaign = { campaign, days -> adminViewModel.saveCampaign(campaign, days) },
                onToggleCampaignActive = { adminViewModel.toggleCampaignActive(it) },
                onDeleteCampaign = { adminViewModel.deleteCampaign(it) },
                homeSections = adminState.homeSections,
                sectionCategories = adminState.sectionCategories,
                onSaveHomeSection = { section, categoryIds ->
                    adminViewModel.saveHomeSection(section, categoryIds)
                },
                onToggleHomeSectionActive = { adminViewModel.toggleHomeSectionActive(it) },
                onDeleteHomeSection = { adminViewModel.deleteHomeSection(it) },
                sponsoredSlots = adminState.sponsoredSlots,
                onReviewSponsoredSlot = { slotId, approve, reason -> adminViewModel.reviewSponsoredSlot(slotId, approve, reason) },
                reviewingSlotId = adminState.reviewingSlotId,
                slotReviewError = adminState.slotReviewError,
                onClearSlotReviewError = { adminViewModel.clearSlotReviewError() },
                dbHealth = adminState.dbHealth,
                tableStats = adminState.tableStats,
                isLoadingDb = adminState.isLoadingDb,
                dbError = adminState.dbError,
                onRefreshDb = { adminViewModel.loadDbHealth() },
                onClearDbError = { adminViewModel.clearDbError() },
                onSignOut = { signOutEverywhere() }
            )
            LaunchedEffect(Unit) {
                adminViewModel.loadDashboard()
                sellerOnboardingViewModel.loadAllForReview()
                deliveryOnboardingViewModel.loadAllForReview()
                orderViewModel.loadIssuesForReview()
            }
        }

        composable(Screen.Splash.route) {
            SplashScreen()
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onSendReset = { authViewModel.sendPasswordReset(it) },
                onBackToLogin = {
                    authViewModel.resetPasswordResetState()
                    navController.popBackStack()
                },
                isLoading = authState.isLoading,
                error = authState.error,
                resetSent = authState.passwordResetSent,
                onClearError = { authViewModel.clearError() }
            )
        }

        composable(
            Screen.ProductDetail.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType }),
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: ""
            // Feed first: Home's grid is paginated, so a card the user just
            // tapped may not be in the full-catalogue dump.
            val productInMemory = homeState.feedProducts.firstOrNull { it.id == productId }
                ?: homeState.products.firstOrNull { it.id == productId }
                ?: favState.favoriteProducts.firstOrNull { it.id == productId }
                ?: sellerState.products.firstOrNull { it.id == productId }

            val productRepo = remember { ProductRepository() }
            var fetchedProduct by remember(productId) { mutableStateOf(productInMemory) }
            var productLoadFailed by remember(productId) { mutableStateOf(false) }

            val reviewRepo = remember { ReviewRepository() }
            var productReviews by remember(productId) { mutableStateOf<List<Review>>(emptyList()) }

            LaunchedEffect(productInMemory) {
                if (fetchedProduct == null && productInMemory != null) {
                    fetchedProduct = productInMemory
                    productLoadFailed = false
                }
            }

            ProductDetailScreen(
                product = fetchedProduct,
                isFavorite = favState.favorites.any { it.productId == productId },
                reviews = productReviews,
                onAddToCart = { p, qty ->
                    if (authState.user == null) {
                        navController.navigate(Screen.Login.route)
                    } else {
                        cartViewModel.addToCart(p, qty)
                        // Stays on the page rather than popping back: the buy
                        // bar flips to "Go to Bag" with the count, so adding
                        // another item from one of the rails below is one tap
                        // away instead of a re-entry into the product.
                    }
                },
                onToggleFavorite = ::toggleFavoriteOrPromptLogin,
                onBack = { navController.popBackStack() },
                loadFailed = productLoadFailed,
                quantityInBag = cartQuantities[productId] ?: 0,
                onGoToBag = {
                    // The Bag screen's own stepper does the adjusting, so the
                    // product page simply hands the customer over to it.
                    navController.navigate(Screen.CustomerCart.route)
                },
                cartQuantities = cartQuantities,
                onProductClick = { navController.navigate(Screen.ProductDetail.createRoute(it.id)) }
            )

            LaunchedEffect(productId) {
                if (fetchedProduct == null) {
                    productRepo.getProduct(productId)
                        .onSuccess { p ->
                            if (p != null) fetchedProduct = p else productLoadFailed = true
                        }
                        .onFailure { productLoadFailed = true }
                }
                reviewRepo.getReviewsForProduct(productId).onSuccess { productReviews = it }
            }

            LaunchedEffect(authState.user) {
                authState.user?.let { favoriteViewModel.loadFavorites(it.id) }
            }
        }

        composable(
            Screen.CustomerAddresses.route,
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) {
            AddressesScreen(
                addresses = addressState.addresses,
                isLoading = addressState.isLoading,
                onSaveAddress = { label, full, isDefault, existingId, lat, lng ->
                    addressViewModel.saveAddress(label, full, isDefault, existingId, lat, lng)
                },
                onDeleteAddress = { addressViewModel.deleteAddress(it) },
                onSetDefault = { addressViewModel.setDefault(it) },
                onBack = { navController.popBackStack() }
            )

            LaunchedEffect(Unit) { addressViewModel.loadAddresses() }
        }

        composable(
            Screen.CustomerCheckout.route,
            enterTransition = { appPushEnter() },
            exitTransition = { appPushExit() },
            popEnterTransition = { appPushPopEnter() },
            popExitTransition = { appPushPopExit() },
        ) {
            val checkoutContext = LocalContext.current
            val paymentScope = rememberCoroutineScope()
            // Address + wallet snapshot taken when the Razorpay sheet opens,
            // consumed when it reports back — the sheet is an external
            // activity, so this composable may recompose any number of times
            // while it is up and must not re-read live state then.
            var pendingOnlinePayment by remember { mutableStateOf<PendingOnlinePayment?>(null) }
            val razorpayResult by RazorpayResultBus.results.collectAsState()

            CheckoutScreen(
                cartItems = cartState.cartItems,
                addresses = addressState.addresses,
                subtotal = cartState.subtotal,
                deliveryFee = cartState.deliveryFee,
                total = cartState.total,
                savings = cartState.savings,
                belowMinimumOrder = cartState.isBelowMinimumOrder,
                minOrderValue = CartState.MIN_ORDER_VALUE,
                isLoading = cartState.isLoading,
                error = cartState.error,
                walletBalance = cartState.walletBalance,
                onManageAddresses = { navController.navigate(Screen.CustomerAddresses.route) },
                onPlaceOrder = { address, lat, lng, walletAmount ->
                    cartViewModel.placeOrder(address, lat, lng, walletAmount)
                },
                onlinePayAvailable = RazorpayPay.isAvailable(),
                onPayOnline = { address, lat, lng, walletAmount ->
                    val payable = (cartViewModel.state.value.total - walletAmount).coerceAtLeast(0.0)
                    if (payable < 1.0) {
                        // Wallet/coupon covers everything — no gateway round trip needed.
                        cartViewModel.placeOrder(address, lat, lng, walletAmount)
                    } else {
                        cartViewModel.setPaymentLoading(true)
                        pendingOnlinePayment = PendingOnlinePayment(address, lat, lng, walletAmount)
                        paymentScope.launch {
                            RazorpayOrderRepository()
                                .createOrder(
                                    (payable * 100).toLong(),
                                    "duggu_${System.currentTimeMillis()}"
                                )
                                .onSuccess { order ->
                                    val activity = checkoutContext as? Activity
                                    if (activity == null) {
                                        pendingOnlinePayment = null
                                        cartViewModel.setPaymentError("Could not open the payment sheet")
                                    } else {
                                        RazorpayPay.startPayment(
                                            activity = activity,
                                            razorpayOrderId = order.orderId,
                                            keyId = order.keyId,
                                            amountPaise = order.amountPaise.toLong(),
                                            storeName = "Duggu Store",
                                            description = "Grocery order payment",
                                            prefillContact = authState.user?.phone?.takeIf { it.isNotBlank() }
                                        )
                                    }
                                }
                                .onFailure {
                                    pendingOnlinePayment = null
                                    cartViewModel.setPaymentError(
                                        "Online payment is unavailable right now — " +
                                            "please pay cash on delivery"
                                    )
                                }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )

            // Consumes the Razorpay sheet's verdict: dismissed/failed surfaces
            // feedback, success is verified server-side and only then becomes
            // an order. The bus is cleared first so a recomposition can never
            // double-place the same payment.
            LaunchedEffect(razorpayResult) {
                val result = razorpayResult ?: return@LaunchedEffect
                val pending = pendingOnlinePayment
                RazorpayResultBus.consume()
                pendingOnlinePayment = null
                when (result) {
                    is RazorpayResult.Dismissed -> cartViewModel.setPaymentLoading(false)
                    is RazorpayResult.Failure ->
                        cartViewModel.setPaymentError(result.description)
                    is RazorpayResult.Success -> {
                        if (pending == null ||
                            result.paymentId.isBlank() || result.signature.isBlank()
                        ) {
                            cartViewModel.setPaymentError(
                                "Payment could not be verified — no order was placed"
                            )
                        } else {
                            RazorpayOrderRepository()
                                .verifyPayment(result.razorpayOrderId, result.paymentId, result.signature)
                                .onSuccess { valid ->
                                    if (valid) {
                                        cartViewModel.placeOrder(
                                            pending.address,
                                            pending.lat,
                                            pending.lng,
                                            pending.walletAmount,
                                            paymentMethod = "razorpay",
                                            paymentId = result.paymentId
                                        )
                                    } else {
                                        cartViewModel.setPaymentError(
                                            "Payment verification failed — no order was placed"
                                        )
                                    }
                                }
                                .onFailure {
                                    cartViewModel.setPaymentError(
                                        "Could not verify the payment — if money left your account, " +
                                            "contact support with payment id ${result.paymentId}"
                                    )
                                }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) { addressViewModel.loadAddresses() }

            if (cartState.orderPlaced) {
                // Leaves the checkout page behind on the way out — the order it
                // was building no longer exists, so going "back" to it would
                // land on an empty bill.
                OrderPlacedDialog(
                    onTrackOrder = {
                        cartViewModel.resetOrderPlaced()
                        navController.navigate(Screen.CustomerOrders.route) {
                            popUpTo(Screen.CustomerHome.route)
                        }
                    },
                    onContinueShopping = {
                        cartViewModel.resetOrderPlaced()
                        navController.navigate(Screen.CustomerHome.route) {
                            popUpTo(Screen.CustomerHome.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(
            Screen.SellerProductForm.route,
            arguments = listOf(
                navArgument("productId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("categoryId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId").orEmpty()
            val initialCategoryId = backStackEntry.arguments?.getString("categoryId").orEmpty()
            val existing = sellerState.products.firstOrNull { it.id == productId }

            AddEditProductScreen(
                product = existing,
                // Only consulted for a new product — see the route's own note.
                initialCategoryId = initialCategoryId,
                // Every category, not the stocked-only browse list: a seller
                // has to be able to file into a category that is still empty,
                // or it could never stop being empty.
                categories = homeState.allCategories,
                sellerId = authState.user?.id ?: "",
                isLoading = sellerState.isLoading,
                error = sellerState.error,
                onSave = { sellerViewModel.saveProduct(it) },
                onBack = { navController.popBackStack() }
            )

            LaunchedEffect(sellerState.productSaved) {
                if (sellerState.productSaved) {
                    sellerViewModel.resetProductSaved()
                    authState.user?.let { sellerViewModel.loadSellerData(it.id) }
                    navController.popBackStack()
                }
            }
        }
    }

    if (showBar) {
        StoreBottomBar(
            items = BottomNav.itemsFor(role),
            selectedKey = if (role == UserRole.CUSTOMER) {
                BottomNav.selectedKeyFor(currentRoute)
            } else {
                dashboardTab.toString()
            },
            // Myntra-style live Bag count: the pink badge on the Bag tab.
            badgeCounts = if (role == UserRole.CUSTOMER) {
                mapOf(Screen.CustomerCart.route to cartState.cartItems.sumOf { it.quantity })
            } else {
                emptyMap()
            },
            onSelect = { key ->
                if (role == UserRole.CUSTOMER) {
                    // A guest can browse Home and Categories, but Favourites and
                    // Account both need a real customer id — send them to sign
                    // in instead of opening a screen with nothing to show.
                    val needsAccount = key == Screen.CustomerFavorites.route ||
                        key == Screen.CustomerAccount.route
                    if (needsAccount && authState.user == null) {
                        navController.navigate(Screen.Login.route)
                    } else if (key == Screen.CustomerHome.route) {
                        // Home is also the popUpTo anchor every other tab uses, so
                        // routing it through the same navigate()+popUpTo(Home)+
                        // launchSingleTop+restoreState combo means popping up to
                        // the very route being navigated to — from a non-tab
                        // screen like Cart, that combination could leave Cart on
                        // top instead of swapping back to Home. A plain pop back
                        // to Home has no such ambiguity.
                        if (currentRoute != Screen.CustomerHome.route) {
                            val poppedToHome = navController.popBackStack(Screen.CustomerHome.route, false)
                            if (!poppedToHome) {
                                navController.navigate(Screen.CustomerHome.route) {
                                    popUpTo(Screen.CustomerHome.route) { inclusive = true }
                                }
                            }
                        } else {
                            homeRetapTick++
                        }
                    } else if (key != currentRoute) {
                        navController.navigate(key) {
                            // Tabs re-enter rather than stack, so tapping around
                            // the bar does not build up a back stack of them.
                            popUpTo(Screen.CustomerHome.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                } else {
                    dashboardTab = key.toIntOrNull() ?: 0
                }
            },
            centre = null,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    }
}

/**
 * Checkout snapshot held while the Razorpay sheet is open: the address, its
 * fix and the wallet split the order will be placed with once the payment
 * verifies. Read at sheet-open time, never live — recompositions while the
 * external sheet is up must not move the goalposts.
 */
private data class PendingOnlinePayment(
    val address: String,
    val lat: Double?,
    val lng: Double?,
    val walletAmount: Int
)
