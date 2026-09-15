package com.duggustore.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duggustore.app.data.model.OrderStatus
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.Order
import com.duggustore.app.data.model.SponsoredSlot
import com.duggustore.app.data.repository.ProductRepository
import com.duggustore.app.data.repository.OrderRepository
import com.duggustore.app.data.repository.SponsoredSlotRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SellerState(
    val isLoading: Boolean = false,
    val products: List<Product> = emptyList(),
    val orders: List<Order> = emptyList(),
    val totalRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val error: String? = null,
    val productSaved: Boolean = false,
    /** Orders that arrived since the last refresh, waiting to be read out. Cleared by [consumeOrderAlerts] once spoken. */
    val newOrderAlerts: List<Order> = emptyList(),
    val sponsoredSlots: List<SponsoredSlot> = emptyList(),
    val isRequestingSlot: Boolean = false,
    val slotRequestError: String? = null
)

class SellerViewModel : ViewModel() {
    private val productRepo = ProductRepository()
    private val orderRepo = OrderRepository()
    private val sponsoredSlotRepo = SponsoredSlotRepository()

    private val _state = MutableStateFlow(SellerState())
    val state: StateFlow<SellerState> = _state

    /**
     * Every order id seen so far, so a refresh can tell a genuinely new order
     * from one that was already there. Null until the first load finishes:
     * without that distinction, opening the dashboard would announce every
     * order the seller already knows about.
     */
    private var knownOrderIds: Set<String>? = null

    fun loadSellerData(sellerId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            try {
                val productsDeferred = async { productRepo.getProductsBySeller(sellerId) }
                val ordersDeferred = async { orderRepo.getSellerOrders(sellerId) }
                val slotsDeferred = async { sponsoredSlotRepo.getMySlots(sellerId) }

                val prods = productsDeferred.await().getOrThrow()
                val ords = ordersDeferred.await().getOrThrow()
                val slots = slotsDeferred.await().getOrElse { emptyList() }

                val alreadySeen = knownOrderIds
                // Only orders still waiting on the seller are worth calling
                // out; one that arrived already accepted needs no reaction.
                val arrived = if (alreadySeen == null) {
                    emptyList()
                } else {
                    ords.filter { it.id !in alreadySeen && it.status == OrderStatus.PENDING.value }
                }
                knownOrderIds = ords.map { it.id }.toSet()

                _state.value = _state.value.copy(
                    isLoading = false,
                    error = null,
                    products = prods,
                    orders = ords,
                    totalOrders = ords.size,
                    totalRevenue = ords.filter { it.status == "delivered" }.sumOf { it.totalAmount },
                    newOrderAlerts = _state.value.newOrderAlerts + arrived,
                    sponsoredSlots = slots
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load seller data")
            }
        }
    }

    /** Called once the announcement has been handed to the speech engine. */
    fun consumeOrderAlerts() {
        if (_state.value.newOrderAlerts.isEmpty()) return
        _state.value = _state.value.copy(newOrderAlerts = emptyList())
    }

    fun saveProduct(product: Product) {
        viewModelScope.launch {
            val result = if (product.id.isEmpty()) {
                productRepo.createProduct(product)
            } else {
                productRepo.updateProduct(product)
            }
            result.onSuccess {
                _state.value = _state.value.copy(productSaved = true)
            }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun deleteProduct(productId: String, sellerId: String) {
        viewModelScope.launch {
            val result = productRepo.deleteProduct(productId)
            result.onSuccess { loadSellerData(sellerId) }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    /**
     * The seller's Accept/Reject/Start-preparing buttons all end up here.
     *
     * SellerDashboard is fed from this view model's own `orders`, not from
     * OrderViewModel's — the dashboard's Accept button used to call through
     * OrderViewModel.updateOrderStatus, which wrote the status correctly but
     * then refreshed only OrderViewModel's own state. The seller's list never
     * reloaded, so the button looked like it did nothing even though the order
     * had, in fact, been accepted. Reloading here, on the view model that
     * actually owns the list the dashboard renders, is what makes the card
     * disappear from "pending" and the next action appear.
     */
    fun updateOrderStatus(orderId: String, status: OrderStatus, sellerId: String) {
        viewModelScope.launch {
            val result = orderRepo.updateOrderStatus(orderId, status.value)
            result.onSuccess { loadSellerData(sellerId) }
            result.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun resetProductSaved() {
        _state.value = _state.value.copy(productSaved = false)
    }

    /** A preview total to show before submitting — the row's real fee always comes back from the database's own generated column. */
    fun quoteSlotFee(durationDays: Int): Int = sponsoredSlotRepo.quoteFee(durationDays)

    /** Collecting the quoted fee happens outside the app for now — this just files the request; an admin approving it is what puts it live. */
    fun requestSponsoredSlot(sellerId: String, productId: String, durationDays: Int, note: String = "") {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRequestingSlot = true, slotRequestError = null)
            val result = sponsoredSlotRepo.requestSlot(sellerId, productId, durationDays, note)
            result.onSuccess {
                sponsoredSlotRepo.getMySlots(sellerId).onSuccess { slots ->
                    _state.value = _state.value.copy(sponsoredSlots = slots)
                }
            }
            result.onFailure { _state.value = _state.value.copy(slotRequestError = it.message) }
            _state.value = _state.value.copy(isRequestingSlot = false)
        }
    }

    fun clearSlotRequestError() {
        _state.value = _state.value.copy(slotRequestError = null)
    }
    /**
     * Sign-out: the seller's products, orders, revenue and slot requests
     * are scoped to that account and must not outlive the session.
     */
    fun clearSession() {
        _state.value = SellerState()
    }

}
