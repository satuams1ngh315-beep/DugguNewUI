package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.Order
import com.duggustore.app.data.model.OrderIssue
import com.duggustore.app.data.model.OrderItem
import com.duggustore.app.data.model.OrderStatus
import com.duggustore.app.data.model.Review
import com.duggustore.app.data.model.StoreNotification
import com.duggustore.app.data.model.WalletTransaction
import com.duggustore.app.data.repository.NotificationsRepository
import com.duggustore.app.data.repository.OrderIssueRepository
import com.duggustore.app.data.repository.OrderRepository
import com.duggustore.app.data.repository.ReviewRepository
import com.duggustore.app.data.repository.TrackingRepository
import com.duggustore.app.data.repository.WalletRepository
import com.duggustore.app.data.repository.walletBalance
import com.duggustore.app.data.model.DeliveryTracking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OrderState(
    val isLoading: Boolean = false,
    /** The rider's last reported position for the order being watched. */
    val tracking: DeliveryTracking? = null,
    val customerOrders: List<Order> = emptyList(),
    val sellerOrders: List<Order> = emptyList(),
    val deliveryOrders: List<Order> = emptyList(),
    val allOrders: List<Order> = emptyList(),
    val selectedOrder: Order? = null,
    val error: String? = null,
    /** Line items for whichever orders have had them fetched — on demand, not with every order list. */
    val orderItemsByOrderId: Map<String, List<OrderItem>> = emptyMap(),
    /** This customer's own reviews on a delivered order, keyed by product id, so "rate this item" shows what's already rated. */
    val myReviewsByOrderId: Map<String, Map<String, Review>> = emptyMap(),
    val walletTransactions: List<WalletTransaction> = emptyList(),
    /** Separate from [isLoading] so opening the wallet does not blank the orders list. */
    val isWalletLoading: Boolean = false,
    /** This customer's own issue reports, keyed by order id, so "report a problem" can show it was already sent. */
    val myIssuesByOrderId: Map<String, List<OrderIssue>> = emptyMap(),
    /** Open issues on a seller's (or, for an admin, every) order, for the resolve screen. */
    val issuesForReview: List<OrderIssue> = emptyList(),
    /** Real-time DB-backed notifications for the current customer, newest first. */
    val dbNotifications: List<StoreNotification> = emptyList(),
    /** IDs of DB notification rows already marked as read. */
    val readNotificationIds: Set<String> = emptySet(),
    /**
     * Whether the notifications table has been read yet.
     *
     * "No rows" and "not asked yet" are different states and the badge has to
     * tell them apart — an empty [dbNotifications] used to mean both, which
     * left the badge guessing from local order state until the first load
     * landed and then correcting itself in front of the user.
     */
    val notificationsLoaded: Boolean = false
) {
    val walletBalance: Int get() = walletTransactions.walletBalance()
}

class OrderViewModel : ViewModel() {
    private val repository = OrderRepository()
    private val trackingRepo = TrackingRepository()
    private val reviewRepo = ReviewRepository()
    private val walletRepo = WalletRepository()
    private val issueRepo = OrderIssueRepository()
    private val notificationsRepo = NotificationsRepository()

    private val _state = MutableStateFlow(OrderState())
    val state: StateFlow<OrderState> = _state

    /** Kept even when the list is empty, so cancel/status updates can still reload. */
    private var lastCustomerId: String? = null
    private var lastSellerId: String? = null

    fun loadCustomerOrders(customerId: String) {
        lastCustomerId = customerId
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = repository.getCustomerOrders(customerId)
            result.onSuccess { orders ->
                _state.value = _state.value.copy(customerOrders = orders, isLoading = false)
            }
            result.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    /** Used when tracking is opened for an order that is not already in [OrderState.customerOrders]. */
    fun loadOrder(orderId: String) {
        if (orderId.isBlank()) return
        viewModelScope.launch {
            repository.getOrder(orderId).onSuccess { order ->
                if (order == null) return@onSuccess
                val current = _state.value.customerOrders
                if (current.none { it.id == order.id }) {
                    _state.value = _state.value.copy(customerOrders = current + order)
                }
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun loadSellerOrders(sellerId: String) {
        lastSellerId = sellerId
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = repository.getSellerOrders(sellerId)
            result.onSuccess { orders ->
                _state.value = _state.value.copy(sellerOrders = orders, isLoading = false)
            }
            result.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun loadDeliveryOrders(deliveryId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = repository.getDeliveryOrders(deliveryId)
            result.onSuccess { orders ->
                _state.value = _state.value.copy(deliveryOrders = orders, isLoading = false)
            }
            result.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun loadAllOrders() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = repository.getAllOrders()
            result.onSuccess { orders ->
                _state.value = _state.value.copy(allOrders = orders, isLoading = false)
            }
            result.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun updateOrderStatus(orderId: String, status: OrderStatus) {
        viewModelScope.launch {
            val result = repository.updateOrderStatus(orderId, status.value)
            result.onSuccess {
                refreshOrders()
            }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            val result = repository.cancelOrder(orderId)
            result.onSuccess { refreshOrders() }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    private fun refreshOrders() {
        lastCustomerId?.let { loadCustomerOrders(it) }
        lastSellerId?.let { loadSellerOrders(it) }
        if (_state.value.allOrders.isNotEmpty()) {
            loadAllOrders()
        }
    }

    /**
     * Reads the rider's last position for an order.
     *
     * The screen calls this on a timer rather than the app holding a socket
     * open: the row changes every few seconds at most, and a poll while the
     * tracking screen is on top costs less than a live subscription that has to
     * be torn down and rebuilt on every navigation.
     */
    fun loadTracking(orderId: String) {
        viewModelScope.launch {
            trackingRepo.getTracking(orderId)
                .onSuccess { _state.value = _state.value.copy(tracking = it) }
            // A failure here is not worth an error banner: the tracking card
            // reads "not sharing" and the rest of the screen is unaffected.
        }
    }

    fun clearTracking() {
        _state.value = _state.value.copy(tracking = null)
    }

    /**
     * Fetched per order rather than embedded in every list query — a
     * seller or customer with dozens of orders would otherwise pull every
     * line item and product row for all of them on every list refresh, for
     * detail almost none of those rows are open at once.
     */
    fun loadOrderItems(orderId: String) {
        if (_state.value.orderItemsByOrderId.containsKey(orderId)) return
        viewModelScope.launch {
            repository.getOrderItems(orderId).onSuccess { items ->
                _state.value = _state.value.copy(
                    orderItemsByOrderId = _state.value.orderItemsByOrderId + (orderId to items)
                )
            }
        }
    }

    /** Only worth calling once an order is delivered — nothing can be rated before that. */
    fun loadMyReviews(userId: String, orderId: String) {
        if (_state.value.myReviewsByOrderId.containsKey(orderId)) return
        viewModelScope.launch {
            reviewRepo.getMyReviewsForOrder(userId, orderId).onSuccess { reviews ->
                _state.value = _state.value.copy(
                    myReviewsByOrderId = _state.value.myReviewsByOrderId +
                        (orderId to reviews.associateBy { it.productId })
                )
            }
        }
    }

    /** Upserts, so rating the same item on the same order again just updates it. */
    fun submitReview(userId: String, orderId: String, productId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            reviewRepo.submitReview(userId, orderId, productId, rating, comment).onSuccess {
                val forOrder = _state.value.myReviewsByOrderId[orderId].orEmpty() +
                    (productId to Review(
                        userId = userId,
                        orderId = orderId,
                        productId = productId,
                        rating = rating,
                        comment = comment
                    ))
                _state.value = _state.value.copy(
                    myReviewsByOrderId = _state.value.myReviewsByOrderId + (orderId to forOrder)
                )
            }
        }
    }

    fun loadWallet(userId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isWalletLoading = true)
            walletRepo.getTransactions(userId).onSuccess { txns ->
                _state.value = _state.value.copy(walletTransactions = txns, isWalletLoading = false)
            }.onFailure {
                _state.value = _state.value.copy(isWalletLoading = false, error = it.message)
            }
        }
    }

    fun loadMyIssues(userId: String, orderId: String) {
        if (_state.value.myIssuesByOrderId.containsKey(orderId)) return
        viewModelScope.launch {
            issueRepo.getIssuesForOrder(userId, orderId).onSuccess { issues ->
                _state.value = _state.value.copy(
                    myIssuesByOrderId = _state.value.myIssuesByOrderId + (orderId to issues)
                )
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun reportIssue(orderId: String, productId: String?, userId: String, reason: String, description: String) {
        viewModelScope.launch {
            issueRepo.reportIssue(orderId, productId, userId, reason, description).onSuccess {
                _state.value = _state.value.copy(myIssuesByOrderId = emptyMap())
                loadMyIssues(userId, orderId)
            }
        }
    }

    fun loadIssuesForReview() {
        viewModelScope.launch {
            issueRepo.getIssuesForReview().onSuccess { issues ->
                _state.value = _state.value.copy(issuesForReview = issues)
            }
        }
    }

    fun resolveIssue(issueId: String, approve: Boolean, refundAmount: Int) {
        viewModelScope.launch {
            issueRepo.resolveIssue(issueId, approve, refundAmount).onSuccess {
                loadIssuesForReview()
            }
        }
    }

    // ---- DB-backed notifications ----------------------------------------

    /**
     * Loads the customer's notifications from the `notifications` table.
     * The trigger `trg_order_status_notify` writes a row whenever a seller
     * or rider updates an order status, so these are real server-side events
     * rather than client-derived order states.
     */
    fun loadNotifications(userId: String) {
        viewModelScope.launch {
            notificationsRepo.getNotifications(userId).onSuccess { rows ->
                with(notificationsRepo) {
                    _state.value = _state.value.copy(
                        dbNotifications = rows.map { it.toStoreNotification() },
                        readNotificationIds = rows.filter { it.isRead }.map { it.id }.toSet(),
                        notificationsLoaded = true
                    )
                }
            }
        }
    }

    /**
     * Marks a single notification read on the server, then shows what the
     * server has rather than what was asked for.
     *
     * The old version flipped local state regardless of the result, so a write
     * that never landed still looked read until the next launch re-read the
     * table and brought it back unread.
     */
    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            val rowsChanged = notificationsRepo.markRead(notificationId).getOrNull() ?: 0
            if (rowsChanged > 0) {
                _state.value = _state.value.copy(
                    readNotificationIds = _state.value.readNotificationIds + notificationId
                )
            }
        }
    }

    /**
     * Marks everything unread as read on the server, then reloads so the list
     * reflects the table rather than the request.
     *
     * A PATCH matching zero rows comes back 200 with an empty body, so the
     * count is the only way to tell a write from a silent no-op; reloading on
     * either outcome means a failed write shows up immediately — the badge
     * stays — instead of clearing now and reappearing on the next launch.
     */
    fun markAllNotificationsRead(userId: String) {
        viewModelScope.launch {
            notificationsRepo.markAllRead(userId)
            loadNotifications(userId)
        }
    }

    /**
     * Sign-out: drops every user-scoped row this ViewModel holds — orders,
     * wallet transactions, reviews, issues and notifications.
     *
     * All the ViewModels are activity-scoped, so they survive sign-out:
     * before this existed, the home bell kept the previous user's unread
     * count (the badge shows for a guest, since `notificationsLoaded`
     * stayed true) and anything that opened the notifications screen read
     * the previous user's rows.
     */
    fun clearSession() {
        _state.value = OrderState()
    }
}
