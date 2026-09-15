package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.Order
import com.duggustore.app.data.model.OrderStatus
import com.duggustore.app.data.repository.OrderRepository
import com.duggustore.app.data.repository.TrackingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DeliveryState(
    val isLoading: Boolean = false,
    /** Packed and waiting for a rider — nobody has claimed these yet. */
    val availableOrders: List<Order> = emptyList(),
    val activeOrders: List<Order> = emptyList(),
    val completedOrders: List<Order> = emptyList(),
    val totalEarnings: Double = 0.0,
    val totalDeliveries: Int = 0,
    val error: String? = null,
    /** A claim attempt that lost the race, surfaced once and then cleared. */
    val claimError: String? = null,
    /** Whether the rider is publishing their position for their active orders. */
    val sharingLocation: Boolean = false,
    val lastSharedAt: String? = null,
    val sharingError: String? = null,
    /** Per-day earnings for the last 7 calendar days, oldest to newest. */
    val dailyEarnings: List<Pair<String, Double>> = emptyList()
)

class DeliveryViewModel : ViewModel() {
    private val orderRepo = OrderRepository()
    private val trackingRepo = TrackingRepository()

    private val _state = MutableStateFlow(DeliveryState())
    val state: StateFlow<DeliveryState> = _state

    fun loadDeliveryData(deliveryId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = orderRepo.getDeliveryOrders(deliveryId)
            result.onSuccess { orders ->
                // getDeliveryOrders is already scoped to delivery_id = this rider,
                // and nothing sets delivery_id before a claim, so the only status
                // that can turn up here pre-delivery is out_for_delivery — a
                // confirmed/preparing order was never assigned to anyone.
                val active = orders.filter { it.status == OrderStatus.OUT_FOR_DELIVERY.value }
                val completed = orders.filter { it.status == OrderStatus.DELIVERED.value }
                _state.value = _state.value.copy(
                    activeOrders = active,
                    completedOrders = completed,
                    totalDeliveries = completed.size,
                    totalEarnings = completed.sumOf { it.deliveryFee },
                    dailyEarnings = lastSevenDaysEarnings(completed),
                    isLoading = false,
                    error = null
                )
            }
            result.onFailure {
                _state.value = _state.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    /** The pool. Every online rider polls this while the Available tab is open. */
    fun loadAvailableOrders() {
        viewModelScope.launch {
            val result = orderRepo.getAvailableOrders()
            result.onSuccess { orders ->
                _state.value = _state.value.copy(availableOrders = orders, error = null)
            }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    /**
     * The rider taps Accept on a pool order. A false result is not a failure —
     * it means another rider's claim landed first — so it surfaces as
     * claimError rather than error, and either way the pool is reloaded so the
     * card the rider was looking at reflects what actually happened.
     */
    fun claimOrder(orderId: String, deliveryId: String) {
        viewModelScope.launch {
            val result = orderRepo.claimOrder(orderId, deliveryId)
            result.onSuccess { claimed ->
                if (claimed) {
                    _state.value = _state.value.copy(claimError = null)
                    loadDeliveryData(deliveryId)
                } else {
                    _state.value = _state.value.copy(
                        claimError = "Another rider already picked this one up"
                    )
                }
                loadAvailableOrders()
            }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun clearClaimError() {
        _state.value = _state.value.copy(claimError = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun updateDeliveryStatus(orderId: String, status: String, deliveryId: String) {
        viewModelScope.launch {
            val result = orderRepo.updateOrderStatus(orderId, status)
            result.onSuccess { loadDeliveryData(deliveryId) }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun markDelivered(orderId: String, deliveryId: String) {
        updateDeliveryStatus(orderId, "delivered", deliveryId)
    }

    fun setSharingLocation(on: Boolean) {
        _state.value = _state.value.copy(
            sharingLocation = on,
            sharingError = if (on) null else _state.value.sharingError
        )
    }

    /**
     * Writes one fix for every order the rider is currently carrying. A rider on
     * a run holds several orders at once and each is tracked separately by the
     * customer watching it, so one position fans out to all of them.
     */
    fun publishLocation(deliveryId: String, latitude: Double, longitude: Double) {
        val orders = _state.value.activeOrders
        if (orders.isEmpty()) return

        viewModelScope.launch {
            var failure: String? = null
            orders.forEach { order ->
                trackingRepo.publishLocation(
                    orderId = order.id,
                    deliveryId = deliveryId,
                    latitude = latitude,
                    longitude = longitude,
                    status = order.status
                ).onFailure { failure = it.message }
            }
            _state.value = _state.value.copy(
                lastSharedAt = System.currentTimeMillis().toString(),
                sharingError = failure
            )
        }
    }

    /** Oldest to newest, mirroring `lastSevenDaysRevenue` in SellerDashboard. */
    private fun lastSevenDaysEarnings(orders: List<Order>): List<Pair<String, Double>> {
        val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val calendar = java.util.Calendar.getInstance()
        val today = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val year = calendar.get(java.util.Calendar.YEAR)

        val earningsByDate = orders
            .groupBy { it.createdAt.take(10) }
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
    /**
     * Sign-out: the rider's order lists and earnings are scoped to that
     * account and must not outlive the session.
     */
    fun clearSession() {
        _state.value = DeliveryState()
    }

}
