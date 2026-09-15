# Implementation Plan: Bug Fixes and Improvements

## Overview

This plan converts 16 requirements (9 bug fixes + 7 UI/UX improvements) into discrete, incremental coding tasks. All changes target existing Kotlin + Jetpack Compose files with no new Gradle dependencies. Tasks are ordered from foundational data-layer fixes up through ViewModel changes, shared UI components, screen updates, and navigation fixes.

---

## Tasks

- [ ] 1. Fix data layer — `OrderIssueRepository` scoped query
  - [ ] 1.1 Add `getIssuesForOrder(userId, orderId)` to `OrderIssueRepository.kt`
    - Implement a Supabase `select` call filtered on both `user_id` and `order_id`
    - Return `Result<List<OrderIssue>>`
    - _Requirements: 4.1_
  - [ ]* 1.2 Write unit test for `getIssuesForOrder` scoping
    - Verify that only issues matching the given `orderId` are returned
    - Test failure path returns `Result.failure`
    - _Requirements: 4.1, 4.3_

- [ ] 2. Fix ViewModels — parallel load, state correctness, and error handling
  - [ ] 2.1 Refactor `AdminViewModel.loadDashboard()` to `async/await` parallel execution
    - Wrap all five repository calls in `async { }` blocks and `awaitAll()`
    - Apply a single batch state update after all awaits
    - Set `error` in the catch block
    - _Requirements: 1.1, 1.2, 1.3_
  - [ ]* 2.2 Write property test for Property 1 — admin dashboard failure sets error
    - **Property 1: Admin dashboard failure sets error for any failing call**
    - **Validates: Requirements 1.3**
    - Use Kotest `forAll` with at least 100 iterations; add `kotest-property` `testImplementation`
  - [ ] 2.3 Refactor `SellerViewModel.loadSellerData()` to `async/await` parallel execution
    - Wrap product and order fetches in `async { }` blocks
    - Apply combined state update after both awaits
    - Set `error` in the catch block
    - _Requirements: 3.1, 3.2, 3.3_
  - [ ]* 2.4 Write property test for Property 2 — seller state reflects both concurrent fetch results
    - **Property 2: Seller state reflects both concurrent fetch results**
    - **Validates: Requirements 3.2**
  - [ ] 2.5 Fix `CartViewModel.placeOrder()` to clear `couponCode` on success
    - Add `couponCode = ""` to the success-branch `copy()` call alongside `couponApplied` and `couponDiscount`
    - _Requirements: 2.1, 2.2_
  - [ ] 2.6 Fix `OrderViewModel.loadMyIssues()` to use scoped repository method
    - Change call from the all-user query to `issueRepo.getIssuesForOrder(userId, orderId)`
    - Add cache-hit guard: skip network call if `orderId` already present in `myIssuesByOrderId`
    - Set `error` on failure
    - _Requirements: 4.1, 4.2, 4.3_
  - [ ]* 2.7 Write property test for Property 3 — issue scope correctness
    - **Property 3: Issue scope correctness**
    - **Validates: Requirements 4.1**
  - [ ] 2.8 Fix `DeliveryViewModel.loadAvailableOrders()` failure path sets error
    - Add `.onFailure { _state.value = _state.value.copy(error = it.message ?: "...") }` to the failure branch
    - Expose `fun clearError()` on `DeliveryViewModel`
    - _Requirements: 6.3_
  - [ ] 2.9 Fix `AuthViewModel.checkCurrentUser()` to clear error first
    - Add `_state.value = _state.value.copy(error = null)` as the very first line of the coroutine body
    - _Requirements: 7.1, 7.2_

- [ ] 3. Add `DeliveryEarningsCard` data to `DeliveryViewModel`
  - [ ] 3.1 Add `dailyEarnings: List<Pair<String, Double>>` field to `DeliveryState`
    - Default value `emptyList()`
    - _Requirements: 14.1, 14.3_
  - [ ] 3.2 Implement `lastSevenDaysEarnings(orders)` private helper in `DeliveryViewModel`
    - Mirror the `lastSevenDaysRevenue` pattern from `SellerDashboard`
    - Sum `deliveryFee` per calendar day; days with no deliveries return `0.0`
    - Call from `loadDeliveryData()` and store result in `dailyEarnings`
    - _Requirements: 14.3, 14.4_
  - [ ]* 3.3 Write property test for Property 10 — daily earnings computation correctness
    - **Property 10: Daily earnings computation correctness**
    - **Validates: Requirements 14.3, 14.4**

- [ ] 4. Add `ErrorRetryBlock` shared composable to `CommonComponents.kt`
  - [ ] 4.1 Implement `ErrorRetryBlock(message: String, onRetry: () -> Unit)` in `CommonComponents.kt`
    - Icon + message text + "Retry" `Button` (containerColor = Teal), centered in `fillMaxSize`
    - _Requirements: 16.1, 16.2, 16.3_
  - [ ]* 4.2 Write property test for Property 11 — error screens show Retry button
    - **Property 11: Error screens show Retry button**
    - **Validates: Requirements 16.1, 16.2, 16.3**

- [ ] 5. Update `OrderListScreen` — loading indicator, search, filter chips, pull-to-refresh, retry
  - [ ] 5.1 Add `isLoading: Boolean` parameter and `LinearProgressIndicator` to `OrderListScreen`
    - Show bar between chip row and list when `isLoading == true`; hide when `false`
    - _Requirements: 5.1, 5.2_
  - [ ]* 5.2 Write property test for Property 4 — orders list loading indicator visibility
    - **Property 4: Orders list loading indicator visibility**
    - **Validates: Requirements 5.1, 5.2**
  - [ ] 5.3 Add `searchQuery` / `onSearchQueryChange` parameters and `OutlinedTextField` search field
    - Place field directly below top bar; apply client-side product-name filter
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  - [ ]* 5.4 Write property test for Property 6 — order search filter correctness
    - **Property 6: Order search filter correctness**
    - **Validates: Requirements 9.2**
  - [ ]* 5.5 Write property test for Property 7 — empty search query shows all orders
    - **Property 7: Empty search query shows all orders**
    - **Validates: Requirements 9.3**
  - [ ] 5.6 Add `selectedStatus` / `onStatusSelected` parameters and `FilterChip` row
    - Chips: `["All", "Active", "Delivered", "Cancelled"]`; place below search field
    - Apply status filter combined with search query
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
  - [ ]* 5.7 Write property test for Property 8 — status filter correctness
    - **Property 8: Status filter correctness**
    - **Validates: Requirements 10.2**
  - [ ]* 5.8 Write property test for Property 9 — combined search and status filter
    - **Property 9: Combined search and status filter**
    - **Validates: Requirements 10.4**
  - [ ] 5.9 Add pull-to-refresh (`PullToRefreshContainer`) and `ErrorRetryBlock` to `OrderListScreen`
    - Wrap `LazyColumn` in `Box(Modifier.nestedScroll(pullState.nestedScrollConnection))`
    - Show `ErrorRetryBlock` when `error != null && !isLoading && orders.isEmpty()`
    - _Requirements: 11.3, 16.2_

- [ ] 6. Update `HomeScreen` — pull-to-refresh and retry UI
  - [ ] 6.1 Add `onRefresh: () -> Unit` and `onRetry: () -> Unit` parameters to `HomeScreen`
    - Wrap scroll container with `PullToRefreshContainer` pattern
    - Show `ErrorRetryBlock` when `error != null && !isLoading && data.isEmpty()`
    - _Requirements: 11.1, 16.1_

- [ ] 7. Update `CartScreen` — pull-to-refresh and retry UI
  - [ ] 7.1 Add `onRefresh: () -> Unit` and `onRetry: () -> Unit` parameters to `CartScreen`
    - Wrap scroll container with `PullToRefreshContainer` pattern
    - Show `ErrorRetryBlock` when `error != null && !isLoading && cartItems.isEmpty()`
    - _Requirements: 11.2, 16.3_

- [ ] 8. Update `FavoritesScreen` and `WalletScreen` — loading indicator guards
  - [ ] 8.1 Add `isLoading: Boolean = false` to `FavoritesScreen`
    - Show `CircularProgressIndicator` when loading; show existing empty state or grid otherwise
    - _Requirements: 13.2_
  - [ ] 8.2 Add `isLoading: Boolean = false` to `WalletScreen`
    - Show `CircularProgressIndicator` when loading; show existing empty state or list otherwise
    - _Requirements: 15.2_

- [ ] 9. Update `AddEditProductScreen` — primary image preview
  - [ ] 9.1 Add primary image preview `Box` above the existing `LazyRow` thumbnail row in `AddEditProductScreen`
    - Use `AsyncImage` from Coil; show `fillMaxWidth`, height `180.dp`, `RoundedCornerShape(14.dp)`
    - Show placeholder icon + "No image set" text when `photos.isEmpty()`
    - _Requirements: 12.1, 12.2, 12.3_

- [ ] 10. Update `SellerDashboard` — orders tab pull-to-refresh
  - [ ] 10.1 Add pull-to-refresh to the orders tab `LazyColumn` in `SellerDashboard`
    - On refresh invoke `sellerViewModel.loadSellerData(sellerId)`
    - _Requirements: 11.4_

- [ ] 11. Update `DeliveryDashboard` — error banner, pull-to-refresh, and `DeliveryEarningsCard`
  - [ ] 11.1 Add error banner to `DeliveryDashboard`
    - Render a `Surface`-based inline error row below the header when `DeliveryState.error != null`
    - Wire dismiss button to `deliveryViewModel.clearError()`
    - _Requirements: 6.1, 6.2_
  - [ ]* 11.2 Write property test for Property 5 — delivery error banner visibility
    - **Property 5: Delivery error banner visibility**
    - **Validates: Requirements 6.1**
  - [ ] 11.3 Add pull-to-refresh to available-orders and active-orders tabs in `DeliveryDashboard`
    - Available tab: invoke `deliveryViewModel.loadAvailableOrders()`
    - Active tab: invoke `deliveryViewModel.loadDeliveryData(deliveryId)`
    - _Requirements: 11.5, 11.6_
  - [ ] 11.4 Add `DeliveryEarningsCard` composable to `DeliveryDashboard.kt`
    - Bar chart of `dailyEarnings` from `DeliveryState`; mirror `WeeklyRevenueCard` structure
    - Zero-height or empty bar for days with no deliveries
    - _Requirements: 14.1, 14.2, 14.4_

- [ ] 12. Fix navigation — ForgotPassword in `CUSTOMER_TOP_LEVEL`
  - [ ] 12.1 Add `Screen.ForgotPassword.route` to `CUSTOMER_TOP_LEVEL` set in `AppShell.kt`
    - Bottom nav bar remains visible when customer navigates to ForgotPassword
    - _Requirements: 8.1, 8.2_

- [ ] 13. Wire new parameters through `NavGraph.kt`
  - [ ] 13.1 Thread new ViewModel state fields and lambdas to all updated screens in `NavGraph.kt`
    - Pass `isLoading`, `onRefresh`, `onRetry`, `searchQuery`, `onSearchQueryChange`, `selectedStatus`, `onStatusSelected`, `error`, `onDismissError`, `dailyEarnings` from the respective ViewModels to screens
    - _Requirements: 5.1, 6.1, 9.1, 10.1, 11.1–11.6, 13.2, 14.1, 15.2, 16.1–16.3_

- [ ] 14. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests use Kotest `kotest-property` (add one `testImplementation` line to `app/build.gradle.kts`)
- All pull-to-refresh implementations use `@OptIn(ExperimentalMaterial3Api::class)` with `rememberPullToRefreshState()` + `PullToRefreshContainer` (M3 1.1.x compatible)
- `ErrorRetryBlock` in `CommonComponents.kt` is shared by `HomeScreen`, `OrderListScreen`, and `CartScreen`

---

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "3.1", "4.1"] },
    { "id": 1, "tasks": ["1.2", "2.1", "2.3", "2.5", "2.8", "2.9", "3.2"] },
    { "id": 2, "tasks": ["2.2", "2.4", "2.6", "3.3", "4.2", "9.1"] },
    { "id": 3, "tasks": ["2.7", "5.1", "5.3", "5.6", "6.1", "7.1", "8.1", "8.2", "10.1", "11.1", "11.4", "12.1"] },
    { "id": 4, "tasks": ["5.2", "5.4", "5.5", "5.9", "11.2", "11.3"] },
    { "id": 5, "tasks": ["5.7", "5.8"] },
    { "id": 6, "tasks": ["13.1"] }
  ]
}
```
