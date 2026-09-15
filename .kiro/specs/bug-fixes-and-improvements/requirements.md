# Requirements Document

## Introduction

This feature addresses 9 confirmed bugs and implements 10 missing UI/UX features in the DugguStore Android application. The app is a multi-role Kotlin + Jetpack Compose e-commerce platform serving customer, seller, delivery, and admin roles backed by Supabase via raw OkHttp. All changes must remain within existing Gradle dependencies; FCM push notifications are explicitly excluded.

---

## Glossary

- **AdminViewModel**: ViewModel responsible for loading the admin dashboard data.
- **CartViewModel**: ViewModel that manages cart state, coupon application, and order placement.
- **SellerViewModel**: ViewModel that loads seller products and orders for the seller dashboard.
- **OrderViewModel**: ViewModel that manages the customer order list and issue tracking.
- **DeliveryViewModel**: ViewModel that manages available and active delivery orders for the rider.
- **AuthViewModel**: ViewModel that manages authentication state and user profile.
- **HomeScreen**: The customer-facing home screen composable.
- **CartScreen**: The customer cart composable.
- **OrderListScreen**: The composable that displays a customer's order history.
- **SellerDashboard**: The composable dashboard for seller-role users.
- **DeliveryDashboard**: The composable dashboard for delivery-role users.
- **FavoritesScreen**: The composable that displays a customer's saved favorite products.
- **WalletScreen**: The composable that displays a customer's wallet transaction history.
- **AddEditProductScreen**: The composable form for creating or editing a seller product.
- **NavGraph**: The navigation graph composable that defines all routes and top-level destinations.
- **CUSTOMER_TOP_LEVEL**: The set of routes in `NavGraph` for which the customer bottom navigation bar is displayed.
- **WeeklyRevenueCard**: The existing seller dashboard composable that renders a 7-day per-day bar chart of revenue.
- **DeliveryEarningsCard**: The new delivery dashboard composable to be created, mirroring `WeeklyRevenueCard` for rider earnings.
- **SupabaseService**: The raw OkHttp-based Supabase API client used for all remote data access.
- **viewModelScope**: The coroutine scope bound to a ViewModel's lifecycle.
- **async/await**: Kotlin coroutines `async { }` / `.await()` pattern for concurrent execution within a single `launch` block.
- **OrderState**: The UI state data class owned by `OrderViewModel`.
- **DeliveryState**: The UI state data class owned by `DeliveryViewModel`.
- **PullToRefresh**: Jetpack Compose `PullToRefreshBox` or equivalent swipe-down gesture that re-fetches screen data.

---

## Requirements

### Requirement 1 — Admin Dashboard Parallel Load

**User Story:** As an admin, I want the dashboard to load all metrics simultaneously so that the screen appears fully populated as fast as possible.

#### Acceptance Criteria

1. WHEN `AdminViewModel.loadDashboard()` is invoked, THE `AdminViewModel` SHALL launch all independent network calls concurrently using `async/await` within a single `viewModelScope.launch` block.
2. WHEN all concurrent calls in `loadDashboard()` complete, THE `AdminViewModel` SHALL update the admin UI state in a single batch so that partial states are not rendered.
3. IF any concurrent call in `loadDashboard()` throws an exception, THEN THE `AdminViewModel` SHALL set the error field in the admin UI state and cancel the remaining awaits.

---

### Requirement 2 — Cart Coupon Code Cleared After Order

**User Story:** As a customer, I want the coupon code field to be blank after I place an order so that I do not accidentally reuse a one-time coupon.

#### Acceptance Criteria

1. WHEN `CartViewModel.placeOrder()` completes successfully, THE `CartViewModel` SHALL set `couponCode`, `couponApplied`, and `couponDiscount` to their respective initial/empty values in the same state update that clears the cart.
2. WHILE a successful order has been placed, THE `CartViewModel` SHALL maintain `couponCode` as an empty string until the user explicitly types a new code.

---

### Requirement 3 — Seller Dashboard Parallel Fetch

**User Story:** As a seller, I want my products and orders to load at the same time so that the dashboard is ready sooner.

#### Acceptance Criteria

1. WHEN `SellerViewModel.loadSellerData()` is invoked, THE `SellerViewModel` SHALL fetch products and orders concurrently using `async/await` within a single `viewModelScope.launch` block.
2. WHEN both concurrent fetches in `loadSellerData()` complete, THE `SellerViewModel` SHALL update the seller UI state with the combined results.
3. IF either concurrent fetch throws an exception, THEN THE `SellerViewModel` SHALL set the error field in the seller UI state.

---

### Requirement 4 — Order Issues Guard Corrected

**User Story:** As a customer, I want the issue list for an order to always be accurate so that I can track the status of a support request.

#### Acceptance Criteria

1. WHEN `OrderViewModel.loadMyIssues` is called with a specific `orderId`, THE `OrderViewModel` SHALL fetch only the issues associated with that `orderId` rather than all issues for the user.
2. WHEN `OrderViewModel.loadMyIssues` is called with an `orderId` whose issues are already loaded, THE `OrderViewModel` SHALL skip the network call and return the cached data.
3. IF the issues fetch fails, THEN THE `OrderViewModel` SHALL set the error field in `OrderState`.

---

### Requirement 5 — Orders List Loading Indicator

**User Story:** As a customer, I want to see a loading indicator on the orders list during a background refresh so that I know the list is being updated.

#### Acceptance Criteria

1. WHILE `OrderState.isLoading` is `true`, THE `OrderListScreen` SHALL display a visible loading indicator (e.g., `CircularProgressIndicator` or top progress bar).
2. WHEN `OrderState.isLoading` transitions to `false`, THE `OrderListScreen` SHALL hide the loading indicator and display the updated orders list.

---

### Requirement 6 — Delivery Error Visibility

**User Story:** As a delivery rider, I want to see error messages on the dashboard so that I know when something has gone wrong.

#### Acceptance Criteria

1. WHEN `DeliveryState.error` is a non-null, non-empty string, THE `DeliveryDashboard` SHALL display the error message to the rider in a visible UI element (e.g., a `Snackbar` or inline error text).
2. WHEN the rider dismisses the error or a subsequent successful load occurs, THE `DeliveryDashboard` SHALL clear the displayed error.
3. WHEN `DeliveryViewModel.loadAvailableOrders()` fails, THE `DeliveryViewModel` SHALL set `DeliveryState.error` to a human-readable message describing the failure.

---

### Requirement 7 — Auth Refresh Clears Prior Error

**User Story:** As a user, I want stale authentication error banners to disappear when I trigger a profile refresh so that I do not see outdated error messages.

#### Acceptance Criteria

1. WHEN `AuthViewModel.refreshProfile()` is invoked, THE `AuthViewModel` SHALL clear the `error` field in auth UI state before issuing any network call.
2. WHEN `AuthViewModel.checkCurrentUser()` begins execution, THE `AuthViewModel` SHALL set the `error` field to `null` or empty as its first state mutation.
3. IF the refresh call fails, THEN THE `AuthViewModel` SHALL set the `error` field to the new failure message, replacing any previously cleared value.

---

### Requirement 8 — ForgotPassword in Top-Level Routes

**User Story:** As a customer, I want the bottom navigation bar to remain visible on the ForgotPassword screen when I navigate to it from certain paths so that I can navigate elsewhere without going back.

#### Acceptance Criteria

1. THE `NavGraph` SHALL include the ForgotPassword route in `CUSTOMER_TOP_LEVEL` (or the equivalent set used to control bottom bar visibility).
2. WHEN a customer navigates to the ForgotPassword screen, THE `AppShell` SHALL render the customer bottom navigation bar.

---

### Requirement 9 — Orders List Search

**User Story:** As a customer, I want to search my orders by product name so that I can quickly find a specific past purchase.

#### Acceptance Criteria

1. THE `OrderListScreen` SHALL display a search input field above the orders list.
2. WHEN the customer types text into the search field, THE `OrderListScreen` SHALL filter the displayed orders to only those containing at least one item whose product name contains the entered text (case-insensitive).
3. WHEN the search field is empty, THE `OrderListScreen` SHALL display all orders unfiltered.
4. WHEN no orders match the search query, THE `OrderListScreen` SHALL display an empty-state message indicating no results were found.

---

### Requirement 10 — Orders List Status Filter

**User Story:** As a customer, I want to filter my orders by status so that I can see only active, delivered, or cancelled orders at a glance.

#### Acceptance Criteria

1. THE `OrderListScreen` SHALL display a row of filter chips (All, Active, Delivered, Cancelled) above the orders list and below the search field.
2. WHEN the customer selects a status chip, THE `OrderListScreen` SHALL display only orders whose status matches the selected chip.
3. WHEN the "All" chip is selected, THE `OrderListScreen` SHALL display orders of every status.
4. THE `OrderListScreen` SHALL apply the status filter in combination with any active search query, so that only orders satisfying both criteria are shown.

---

### Requirement 11 — Pull-to-Refresh

**User Story:** As a user, I want to swipe down on list screens to manually refresh the data so that I can get the latest information without navigating away.

#### Acceptance Criteria

1. THE `HomeScreen` SHALL support a pull-to-refresh gesture that invokes the `HomeViewModel` data-reload function.
2. THE `CartScreen` SHALL support a pull-to-refresh gesture that invokes the `CartViewModel` data-reload function.
3. THE `OrderListScreen` SHALL support a pull-to-refresh gesture that invokes `OrderViewModel.loadOrders()`.
4. THE `SellerDashboard` orders tab SHALL support a pull-to-refresh gesture that invokes the relevant `SellerViewModel` reload function.
5. THE `DeliveryDashboard` available-orders tab SHALL support a pull-to-refresh gesture that invokes `DeliveryViewModel.loadAvailableOrders()`.
6. THE `DeliveryDashboard` active-orders tab SHALL support a pull-to-refresh gesture that invokes the relevant active-orders reload function.
7. WHILE a pull-to-refresh operation is in progress, THE affected screen SHALL display the platform refresh indicator until the reload completes or fails.

---

### Requirement 12 — Seller Product Form Image Preview

**User Story:** As a seller, I want to see a thumbnail of the product image inside the add/edit product form so that I can confirm the correct image before saving.

#### Acceptance Criteria

1. WHEN `AddEditProductScreen` is opened for an existing product that has an image URL, THE `AddEditProductScreen` SHALL render a thumbnail of that image using the existing Coil image-loading library.
2. WHEN the seller selects or uploads a new image, THE `AddEditProductScreen` SHALL update the thumbnail to reflect the newly selected image immediately.
3. WHEN no image has been provided, THE `AddEditProductScreen` SHALL render a placeholder indicating that no image is set.

---

### Requirement 13 — Favorites Empty State

**User Story:** As a customer, I want to see a friendly message when my favorites list is empty so that the screen does not appear broken.

#### Acceptance Criteria

1. WHEN the favorites list returned by `FavoriteViewModel` is empty, THE `FavoritesScreen` SHALL display an empty-state illustration or icon with descriptive copy (e.g., "No favourites yet").
2. WHILE the favorites list is loading, THE `FavoritesScreen` SHALL display a loading indicator rather than the empty state.

---

### Requirement 14 — Delivery Weekly Earnings Card

**User Story:** As a delivery rider, I want to see a bar chart of my earnings for the last 7 days so that I can track my daily performance.

#### Acceptance Criteria

1. THE `DeliveryDashboard` SHALL include a `DeliveryEarningsCard` composable that displays a bar chart of per-day earnings for the last 7 days.
2. THE `DeliveryEarningsCard` SHALL follow the same visual and structural pattern as the existing `WeeklyRevenueCard` composable in `SellerDashboard`.
3. WHEN `DeliveryViewModel` loads earnings data, THE `DeliveryViewModel` SHALL compute per-day totals for the last 7 calendar days from the rider's completed deliveries.
4. WHEN no completed deliveries exist for a given day, THE `DeliveryEarningsCard` SHALL render a zero-height or clearly empty bar for that day.

---

### Requirement 15 — Wallet Empty State

**User Story:** As a customer, I want to see a friendly message when my wallet has no transactions so that the screen does not appear broken.

#### Acceptance Criteria

1. WHEN `walletTransactions` in the wallet UI state is empty, THE `WalletScreen` SHALL display an empty-state message (e.g., "No transactions yet").
2. WHILE wallet data is loading, THE `WalletScreen` SHALL display a loading indicator rather than the empty state.

---

### Requirement 16 — Network Error Retry UI

**User Story:** As a user, I want a "Retry" button on error states so that I can recover from a network failure without restarting the app.

#### Acceptance Criteria

1. WHEN `HomeScreen` displays a network error state, THE `HomeScreen` SHALL render a "Retry" button that invokes the `HomeViewModel` data-reload function.
2. WHEN `OrderListScreen` displays a network error state, THE `OrderListScreen` SHALL render a "Retry" button that invokes `OrderViewModel.loadOrders()`.
3. WHEN `CartScreen` displays a network error state, THE `CartScreen` SHALL render a "Retry" button that invokes the `CartViewModel` data-reload function.
4. WHEN the retry action is triggered, THE affected screen SHALL display a loading indicator while the reload is in progress.
