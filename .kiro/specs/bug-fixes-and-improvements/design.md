# Design Document

## Feature: Bug Fixes and Improvements

---

## Introduction

This document describes the architecture and implementation plan for fixing 9 confirmed bugs and adding 7 missing UI/UX features in the DugguStore Android application. All changes are confined to existing Gradle dependencies (Kotlin, Jetpack Compose, Coroutines, Coil, OkHttp/Supabase). No new dependencies are introduced.

---

## Architecture Overview

The app follows a single-activity, Compose-only architecture with a flat `NavGraph`. State flows downward from `ViewModel → Screen composable`; user actions flow upward via lambdas. ViewModels hold `MutableStateFlow<*State>`, collected in screens with `collectAsStateWithLifecycle()`. There is no Hilt — every `ViewModel` is constructed with the no-arg `viewModel()` call in the `NavGraph` composable.

```
NavGraph (owns all ViewModel instances)
 └─ AppShell (renders BottomNav for the current route)
     └─ Screen composables (stateless, driven by ViewModel state)
          └─ Repository calls (via ViewModel coroutines on Dispatchers.IO)
               └─ SupabaseService (OkHttp, raw REST / RPC)
```

Each bug fix and feature addition is scoped to the minimal surface required: one or two files per item. The changes deliberately preserve the existing file and class structure.

---

## Component Descriptions

### ViewModels

| ViewModel | Change |
|---|---|
| `AdminViewModel` | `loadDashboard()` converted to `async/await` parallel execution |
| `SellerViewModel` | `loadSellerData()` converted to `async/await` parallel execution |
| `CartViewModel` | `placeOrder()` success branch now also clears `couponCode` |
| `OrderViewModel` | `loadMyIssues()` guard fixed; new `loadOrders(customerId)` convenience function added for pull-to-refresh and retry |
| `DeliveryViewModel` | `loadAvailableOrders()` failure path sets `DeliveryState.error`; `dailyEarnings` list added to `DeliveryState` for the earnings card |
| `AuthViewModel` | `checkCurrentUser()` clears `error` as its very first state mutation |

### Screen Composables

| Screen | Change |
|---|---|
| `OrderListScreen` | Adds `isLoading` indicator, search field, status filter chips, pull-to-refresh, and retry UI |
| `HomeScreen` | Adds pull-to-refresh and retry UI |
| `CartScreen` | Adds pull-to-refresh and retry UI |
| `FavoritesScreen` | Adds `isLoading` parameter and loading indicator guard |
| `WalletScreen` | Adds `isLoading` parameter and loading indicator guard |
| `AddEditProductScreen` | Adds Coil image preview thumbnail |
| `SellerDashboard` | Orders tab gains pull-to-refresh |
| `DeliveryDashboard` | Available/active tabs gain pull-to-refresh; error snackbar added; new `DeliveryEarningsCard` composable added |

### Navigation

| File | Change |
|---|---|
| `AppShell.kt` | `Screen.ForgotPassword.route` added to `CUSTOMER_TOP_LEVEL` |

---

## Data Model Changes

### `DeliveryState` — new field

```kotlin
data class DeliveryState(
    // ... existing fields ...
    /** Per-day earnings for the last 7 calendar days, oldest first. */
    val dailyEarnings: List<Pair<String, Double>> = emptyList()
)
```

`dailyEarnings` is computed inside `loadDeliveryData()` from `completedOrders` using the same `lastSevenDays*` pattern used by `WeeklyRevenueCard` in `SellerDashboard`.

### `OrderIssueRepository` — new method

The repository gains `getIssuesForOrder(userId, orderId)` that scopes the Supabase query with both `user_id` and `order_id` filters, instead of the current `getMyIssues(userId)` which returns all issues and then groups client-side. This is the root fix for Requirement 4.

```kotlin
suspend fun getIssuesForOrder(userId: String, orderId: String): Result<List<OrderIssue>>
```

---

## Interfaces

### Pull-to-Refresh

The Compose BOM `2023.10.01` includes `androidx.compose.material3:material3` which ships `PullToRefreshBox` (M3 `1.3.x`) — however the pinned BOM version in this project resolves to M3 `1.1.x`, which does **not** include `PullToRefreshBox`. To stay within the declared dependencies without a version bump, pull-to-refresh is implemented using the lower-level `rememberPullToRefreshState()` / `PullToRefreshContainer` from `androidx.compose.material3` that **is** available in M3 `1.1.x`, or equivalently the `Modifier.pullToRefresh` + `PullToRefreshIndicator` from `androidx.compose.material3` experimental APIs. In practice the simplest approach is:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
val pullState = rememberPullToRefreshState()
Box(Modifier.nestedScroll(pullState.nestedScrollConnection)) {
    // screen content
    if (pullState.isRefreshing) {
        LaunchedEffect(Unit) {
            reload()
            pullState.endRefresh()
        }
    }
    PullToRefreshContainer(state = pullState, modifier = Modifier.align(Alignment.TopCenter))
}
```

Every affected screen wraps its existing `LazyColumn` (or `Column`) in this `Box` and delegates to the appropriate ViewModel reload function.

### Retry UI

A shared private composable `ErrorRetryBlock(message, onRetry)` is added to each affected screen file (or to `CommonComponents.kt` for reuse):

```kotlin
@Composable
fun ErrorRetryBlock(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = TextLight, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, fontSize = 15.sp, color = TextSecondary, textAlign = TextAlign.Center,
             modifier = Modifier.padding(horizontal = 40.dp))
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
            Text("Retry", fontWeight = FontWeight.Bold)
        }
    }
}
```

Screens display `ErrorRetryBlock` when `state.error != null && !state.isLoading && data.isEmpty()`.

### Search + Filter on OrderListScreen

`OrderListScreen` receives two new parameters:

```kotlin
searchQuery: String,
onSearchQueryChange: (String) -> Unit,
selectedStatus: String,           // "All" | "Active" | "Delivered" | "Cancelled"
onStatusSelected: (String) -> Unit,
isLoading: Boolean,
onRetry: () -> Unit,
onRefresh: () -> Unit,
```

Filtering is applied client-side inside the composable:

```kotlin
val STATUS_CHIPS = listOf("All", "Active", "Delivered", "Cancelled")

val statusValues = mapOf(
    "Active"    to setOf("pending", "confirmed", "preparing", "ready_for_pickup", "out_for_delivery"),
    "Delivered" to setOf("delivered"),
    "Cancelled" to setOf("cancelled")
)

val displayed = orders
    .filter { order ->
        searchQuery.isBlank() || order.items.any { item ->
            item.product?.name?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    .filter { order ->
        selectedStatus == "All" || order.status in (statusValues[selectedStatus] ?: emptySet())
    }
```

The search field is placed directly below the top bar; the chip row is placed below the search field.

---

## Error Handling

### ViewModel-level

All ViewModel coroutines already follow `result.onFailure { _state.value = _state.value.copy(error = it.message) }`. The two specific gaps closed by this feature:

1. `loadAvailableOrders()` in `DeliveryViewModel` — currently silently sets `availableOrders = emptyList()` on failure. Fixed to also set `error`.
2. `loadMyIssues()` in `OrderViewModel` — currently merges all issues into the map incorrectly. Fixed to call a scoped repository method.

### Screen-level

Screens already conditionally render content vs. empty state. The new pattern is:

```
isLoading=true  → show loading indicator (replaces content)
error!=null && data.isEmpty() → show ErrorRetryBlock
data.isEmpty() && !isLoading && error==null → show empty state illustration
data.isNotEmpty() → show list
```

---

## Implementation Details per Requirement

### Req 1 — Admin Dashboard Parallel Load

`AdminViewModel.loadDashboard()` currently executes five `await`-less sequential calls. Fix: wrap all five in `async { }` blocks and `awaitAll()`:

```kotlin
fun loadDashboard() {
    viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val usersDeferred      = async { authRepo.getAllUsers() }
            val ordersDeferred     = async { orderRepo.getAllOrders() }
            val productsDeferred   = async { productRepo.getAllProducts() }
            val categoriesDeferred = async { categoryRepo.getAllCategories() }
            val couponsDeferred    = async { offerRepo.getAllCoupons() }

            val users      = usersDeferred.await()
            val orders     = ordersDeferred.await()
            val products   = productsDeferred.await()
            val categories = categoriesDeferred.await()
            val coupons    = couponsDeferred.await()

            // single batch update
            _state.value = _state.value.copy(
                users       = users.getOrDefault(emptyList()),
                totalUsers  = users.getOrDefault(emptyList()).size,
                orders      = orders.getOrDefault(emptyList()),
                totalOrders = orders.getOrDefault(emptyList()).size,
                totalRevenue   = orders.getOrDefault(emptyList()).filter { it.status == "delivered" }.sumOf { it.totalAmount },
                totalDeliveries = orders.getOrDefault(emptyList()).count { it.status == "delivered" },
                products    = products.getOrDefault(emptyList()),
                categories  = categories.getOrDefault(emptyList()),
                coupons     = coupons.getOrDefault(emptyList()),
                isLoading   = false
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e.message)
        }
    }
}
```

Using `async {}` with a `try/catch` around all `.await()` calls means the first failure cancels the rest and lands in the catch block (structured concurrency default behaviour).

### Req 2 — Cart Coupon Cleared

In `CartViewModel.placeOrder()`, the success branch already resets `couponApplied`, `couponDiscount`, and `couponError` but **omits** `couponCode`. One-line fix:

```kotlin
_state.value = _state.value.copy(
    isLoading      = false,
    orderPlaced    = true,
    cartItems      = emptyList(),
    isCartOpen     = false,
    couponCode     = "",          // ← added
    couponApplied  = false,
    couponDiscount = 0.0,
    couponError    = null,
    walletBalance  = state.walletBalance - walletUsed
)
```

### Req 3 — Seller Dashboard Parallel Fetch

`SellerViewModel.loadSellerData()` currently runs product and order fetches sequentially. Fix mirrors Req 1: wrap both in `async {}` then `await()`:

```kotlin
fun loadSellerData(sellerId: String) {
    viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val productsDeferred = async { productRepo.getProductsBySeller(sellerId) }
            val ordersDeferred   = async { orderRepo.getSellerOrders(sellerId) }

            val products = productsDeferred.await()
            val orders   = ordersDeferred.await()

            // ... existing logic using products and orders Results ...
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message)
        } finally {
            _state.value = _state.value.copy(isLoading = false)
        }
    }
}
```

### Req 4 — Order Issues Guard

`OrderIssueRepository` gains:

```kotlin
suspend fun getIssuesForOrder(userId: String, orderId: String): Result<List<OrderIssue>> {
    return try {
        val rows = SupabaseService.select(
            "order_issues", token(),
            mapOf("user_id" to userId, "order_id" to orderId)
        )
        Result.success(rows.map { json.decodeFromString(OrderIssue.serializer(), it.toString()) })
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

`OrderViewModel.loadMyIssues()` is updated:

```kotlin
fun loadMyIssues(userId: String, orderId: String) {
    if (_state.value.myIssuesByOrderId.containsKey(orderId)) return   // cache hit
    viewModelScope.launch {
        issueRepo.getIssuesForOrder(userId, orderId)                   // scoped call
            .onSuccess { issues ->
                _state.value = _state.value.copy(
                    myIssuesByOrderId = _state.value.myIssuesByOrderId + (orderId to issues)
                )
            }
            .onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
    }
}
```

### Req 5 — Orders List Loading Indicator

`OrderListScreen` receives `isLoading: Boolean`. Inside the screen body, before the orders list or empty state, a `LinearProgressIndicator` is rendered when `isLoading`:

```kotlin
if (isLoading) {
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Teal)
}
```

This sits between the chip row and the list body, so the structure is never replaced — the progress bar overlays the transition to content.

### Req 6 — Delivery Error Visibility

`DeliveryDashboard` receives `error: String?` and `onDismissError: () -> Unit` parameters. A `Snackbar`-style banner is rendered below the header when error is non-null:

```kotlin
error?.let { msg ->
    Surface(color = CoralSurface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(msg, fontSize = 13.sp, color = Coral, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismissError) {
                Icon(Icons.Default.Close, null, tint = Coral, modifier = Modifier.size(16.dp))
            }
        }
    }
}
```

`DeliveryViewModel` exposes `fun clearError() { _state.value = _state.value.copy(error = null) }`.

`loadAvailableOrders()` failure path:

```kotlin
result.onFailure {
    _state.value = _state.value.copy(
        error = it.message ?: "Couldn't load available orders — try again"
    )
}
```

### Req 7 — Auth Refresh Clears Prior Error

`checkCurrentUser()` (called by `refreshProfile()`) currently does not clear `error` before the network call. Fix: add `error = null` as the first mutation:

```kotlin
private fun checkCurrentUser() {
    viewModelScope.launch {
        _state.value = _state.value.copy(error = null)   // ← clear prior error first
        if (!SessionManager.isLoggedIn()) {
            _state.value = _state.value.copy(isRestoringSession = false)
            return@launch
        }
        _state.value = _state.value.copy(isLoading = true)
        // ... rest unchanged ...
    }
}
```

### Req 8 — ForgotPassword in Top-Level Routes

`AppShell.kt`, inside `BottomNav`:

```kotlin
private val CUSTOMER_TOP_LEVEL = setOf(
    Screen.CustomerHome.route,
    Screen.CustomerCategories.route,
    Screen.CustomerFavorites.route,
    Screen.CustomerAccount.route,
    Screen.CustomerOrders.route,
    Screen.CustomerCart.route,
    Screen.ForgotPassword.route      // ← added
)
```

### Req 9 — Orders List Search

New parameters on `OrderListScreen`:

```kotlin
searchQuery: String = "",
onSearchQueryChange: (String) -> Unit = {},
```

Search field rendered as an `OutlinedTextField` (styled to match existing `AuthField` pattern) directly below the top bar. Filtering is client-side (see Interfaces section above).

### Req 10 — Orders List Status Filter

New parameters:

```kotlin
selectedStatus: String = "All",
onStatusSelected: (String) -> Unit = {},
```

Chip row rendered as a `LazyRow` of `FilterChip` composables with labels `["All", "Active", "Delivered", "Cancelled"]`. Filtering combined with search query (see Interfaces section).

### Req 11 — Pull-to-Refresh

Each affected screen wraps its scroll container in `Box(Modifier.nestedScroll(pullState.nestedScrollConnection))` with a `PullToRefreshContainer` composable. The reload lambdas are:

| Screen | Reload call |
|---|---|
| `HomeScreen` | `onRefresh: () -> Unit` parameter (caller passes `homeViewModel.loadData(...)`) |
| `CartScreen` | `onRefresh: () -> Unit` → `cartViewModel.loadCart()` |
| `OrderListScreen` | `onRefresh: () -> Unit` → `orderViewModel.loadCustomerOrders(customerId)` |
| `SellerDashboard` orders tab | `onRefresh: () -> Unit` → `sellerViewModel.loadSellerData(sellerId)` |
| `DeliveryDashboard` available tab | `onRefresh: () -> Unit` → `deliveryViewModel.loadAvailableOrders()` |
| `DeliveryDashboard` active tab | `onRefresh: () -> Unit` → `deliveryViewModel.loadDeliveryData(deliveryId)` |

### Req 12 — Seller Product Form Image Preview

`AddEditProductScreen` currently shows a `LazyRow` of thumbnail `AsyncImage` composables for `photos` (which is the list of image URLs). The requirement asks for a primary preview when there is at least one image. Since `AsyncImage` from Coil is already used in the screen, the only addition is a larger primary preview `Box` above the `LazyRow`:

```kotlin
// Primary preview (first image or placeholder)
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(SurfaceMuted)
        .border(1.dp, BorderGray, RoundedCornerShape(14.dp)),
    contentAlignment = Alignment.Center
) {
    val previewUrl = photos.firstOrNull()
    if (previewUrl != null) {
        AsyncImage(
            model = previewUrl,
            contentDescription = "Product image preview",
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Image, null, tint = TextLight, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text("No image set", fontSize = 12.sp, color = TextLight)
        }
    }
}
```

The `photos` state is updated whenever the user adds/removes an image (already wired), so the preview automatically reflects the latest selection.

### Req 13 — Favorites Empty State

`FavoritesScreen` already has a complete empty-state implementation. The only gap is that the screen does not receive or show a loading indicator. Fix: add `isLoading: Boolean = false` parameter and guard the content:

```kotlin
if (isLoading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Teal)
    }
} else if (favoriteProducts.isEmpty()) {
    // existing empty state block
} else {
    // existing LazyVerticalGrid
}
```

The `FavoriteViewModel` already has `isLoading` in its state; the caller in `NavGraph` passes it through.

### Req 14 — Delivery Weekly Earnings Card

`DeliveryState` gains `dailyEarnings: List<Pair<String, Double>>`.

`loadDeliveryData()` computes it:

```kotlin
val dailyEarnings = lastSevenDaysEarnings(completed)
_state.value = _state.value.copy(
    // ... existing fields ...
    dailyEarnings = dailyEarnings
)
```

Private helper (mirrors `lastSevenDaysRevenue` in `SellerDashboard`):

```kotlin
private fun lastSevenDaysEarnings(orders: List<Order>): List<Pair<String, Double>> {
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val calendar  = java.util.Calendar.getInstance()
    val today     = calendar.get(java.util.Calendar.DAY_OF_YEAR)
    val year      = calendar.get(java.util.Calendar.YEAR)
    val earningsByDate = orders.groupBy { it.createdAt.take(10) }
        .mapValues { (_, group) -> group.sumOf { it.deliveryFee } }
    return (6 downTo 0).map { daysAgo ->
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.DAY_OF_YEAR, today)
            add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        }
        val dateKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
        val weekday = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        dayLabels[weekday] to (earningsByDate[dateKey] ?: 0.0)
    }
}
```

`DeliveryEarningsCard` composable in `DeliveryDashboard.kt`:

```kotlin
@Composable
private fun DeliveryEarningsCard(dailyEarnings: List<Pair<String, Double>>) {
    val maxEarnings = dailyEarnings.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    DashboardPanel {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Earnings, last 7 days", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dailyEarnings.forEach { (label, earning) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((60 * (earning / maxEarnings)).dp.coerceAtLeast(3.dp))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (earning > 0) Teal else BorderGray)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(label, fontSize = 10.sp, color = TextLight)
                    }
                }
            }
        }
    }
}
```

### Req 15 — Wallet Empty State

`WalletScreen` already has the empty-state body text ("No wallet activity yet"). The only missing piece is a loading indicator guard. Fix: add `isLoading: Boolean = false` parameter:

```kotlin
if (isLoading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Teal)
    }
} else if (transactions.isEmpty()) {
    // existing empty state
} else {
    // existing LazyColumn
}
```

### Req 16 — Network Error Retry UI

`ErrorRetryBlock` (defined in the Interfaces section) is rendered in `HomeScreen`, `OrderListScreen`, and `CartScreen` when `error != null && !isLoading && data.isEmpty()`. The retry lambda calls the same function used by pull-to-refresh (`loadData`, `loadCart`, `loadCustomerOrders`).

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Admin dashboard failure sets error for any failing call

*For any* invocation of `AdminViewModel.loadDashboard()` where at least one of the five concurrent network calls throws an exception, the resulting `AdminState.error` SHALL be a non-null string and `AdminState.isLoading` SHALL be `false`.

**Validates: Requirements 1.3**

---

### Property 2: Seller state reflects both concurrent fetch results

*For any* products list and orders list returned by the respective repositories, after `SellerViewModel.loadSellerData()` completes successfully, `SellerState.products` SHALL equal the fetched products list and `SellerState.orders` SHALL equal the fetched orders list.

**Validates: Requirements 3.2**

---

### Property 3: Issue scope correctness

*For any* `orderId` and *any* list of `OrderIssue` objects (some with that `orderId`, some with different ones), after `OrderViewModel.loadMyIssues(userId, orderId)` completes, `OrderState.myIssuesByOrderId[orderId]` SHALL contain exactly the issues whose `orderId` field matches the requested `orderId` and SHALL NOT contain issues belonging to other orders.

**Validates: Requirements 4.1**

---

### Property 4: Orders list loading indicator visibility

*For any* `OrderState` where `isLoading` is `true`, the `OrderListScreen` composable SHALL render a visible progress indicator node. *For any* `OrderState` where `isLoading` is `false`, the progress indicator SHALL NOT be visible.

**Validates: Requirements 5.1, 5.2**

---

### Property 5: Delivery error banner visibility

*For any* non-null, non-empty `error` string in `DeliveryState`, the `DeliveryDashboard` composable SHALL render a UI node containing that exact error string.

**Validates: Requirements 6.1**

---

### Property 6: Order search filter correctness

*For any* list of `Order` objects (each with pre-loaded `OrderItem` entries) and *any* non-blank search query string, every order displayed by `OrderListScreen` SHALL contain at least one item whose `product.name` contains the query string (case-insensitive), and no order failing that predicate SHALL be displayed.

**Validates: Requirements 9.2**

---

### Property 7: Empty search query shows all orders

*For any* list of orders and an empty (or blank) search query string, `OrderListScreen` SHALL display all orders in the list without filtering any out.

**Validates: Requirements 9.3**

---

### Property 8: Status filter correctness

*For any* list of orders and *any* selected status chip (other than "All"), every displayed order SHALL have a status value that belongs to the chip's matching status set, and no order with a non-matching status SHALL be displayed.

**Validates: Requirements 10.2**

---

### Property 9: Combined search and status filter

*For any* order list, search query, and selected status, every order displayed by `OrderListScreen` SHALL simultaneously satisfy both the search predicate (product name match) and the status predicate (status in chip's value set).

**Validates: Requirements 10.4**

---

### Property 10: Daily earnings computation correctness

*For any* list of completed delivery orders with arbitrary dates spanning the last 7 calendar days, the per-day earnings values computed by `lastSevenDaysEarnings()` SHALL equal the sum of `deliveryFee` for all orders whose `createdAt` date matches that day, and days with no deliveries SHALL produce a value of `0.0`.

**Validates: Requirements 14.3, 14.4**

---

### Property 11: Error screens show Retry button

*For any* non-null, non-empty error string passed to `HomeScreen`, `OrderListScreen`, or `CartScreen` when the corresponding data list is empty and `isLoading` is `false`, the composable SHALL render a button whose label is "Retry".

**Validates: Requirements 16.1, 16.2, 16.3**

---

## Files to be Modified

| File | Nature of Change |
|---|---|
| `ui/viewmodel/AdminViewModel.kt` | `loadDashboard()` → async/await, single batch update, error on failure |
| `ui/viewmodel/CartViewModel.kt` | `placeOrder()` success adds `couponCode = ""` |
| `ui/viewmodel/SellerViewModel.kt` | `loadSellerData()` → async/await |
| `ui/viewmodel/OrderViewModel.kt` | `loadMyIssues()` guard fix; `loadOrders()` alias |
| `ui/viewmodel/DeliveryViewModel.kt` | `loadAvailableOrders()` sets error on failure; `DeliveryState` gains `dailyEarnings`; `loadDeliveryData()` computes earnings; `clearError()` added |
| `ui/viewmodel/AuthViewModel.kt` | `checkCurrentUser()` clears error first |
| `ui/navigation/AppShell.kt` | `CUSTOMER_TOP_LEVEL` gains `Screen.ForgotPassword.route` |
| `data/repository/OrderIssueRepository.kt` | `getIssuesForOrder(userId, orderId)` added |
| `ui/screens/customer/OrderTrackingScreen.kt` | `OrderListScreen` gets search, chips, loading indicator, pull-to-refresh, retry |
| `ui/screens/customer/HomeScreen.kt` | Pull-to-refresh, retry UI |
| `ui/screens/customer/CartScreen.kt` | Pull-to-refresh, retry UI |
| `ui/screens/customer/FavoritesScreen.kt` | `isLoading` parameter, loading indicator guard |
| `ui/screens/customer/WalletScreen.kt` | `isLoading` parameter, loading indicator guard |
| `ui/screens/seller/AddEditProductScreen.kt` | Primary image preview box |
| `ui/screens/seller/SellerDashboard.kt` | Orders tab pull-to-refresh |
| `ui/screens/delivery/DeliveryDashboard.kt` | Error banner, pull-to-refresh on available/active tabs, `DeliveryEarningsCard` added |
| `ui/components/CommonComponents.kt` | `ErrorRetryBlock` composable added |
| `ui/navigation/NavGraph.kt` | Thread new parameters (`isLoading`, `onRefresh`, `onRetry`, `searchQuery`, etc.) from ViewModels to screens |

---

## Testing Strategy

This feature involves two complementary layers:

**Unit tests** (example-based) cover:
- Each ViewModel function (loadDashboard, loadSellerData, placeOrder, loadMyIssues, checkCurrentUser) using `TestCoroutineDispatcher` / `runTest`
- Coupon code cleared after order placement
- Error state transitions (failure paths)
- Pull-to-refresh and Retry button callbacks

**Property-based tests** cover Properties 1–11 above using the [Kotest](https://kotest.io/) `forAll` / `Exhaustive` or [junit-quickcheck](https://pholser.github.io/junit-quickcheck/) generators. Since the project has no existing test framework, Kotest with its built-in property testing (`kotest-property`) is the recommended addition — it requires only a `testImplementation` entry and works with the existing JUnit runner.

Each property test tag format: `Feature: bug-fixes-and-improvements, Property {N}: {property_text}`.

Minimum 100 iterations per property test.
