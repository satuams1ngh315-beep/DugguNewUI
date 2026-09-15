package com.duggustore.app.data.repository

import com.duggustore.app.data.model.Order
import com.duggustore.app.data.model.OrderItem
import com.duggustore.app.data.model.OrderStatus
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/**
 * Embeds the seller's store location and the customer's phone on the order
 * row via their FKs, so a rider's navigate-to-pickup button and call-customer
 * button both have somewhere to point without a second round trip per order.
 */
private const val ORDER_WITH_SELLER_SELECT =
    "*,seller:profiles!seller_id(full_name,phone,store_address,store_latitude,store_longitude)," +
        "customer:profiles!customer_id(full_name,phone)"

/** Embeds the rider's phone once one has claimed the order, so the customer can call them. */
private const val ORDER_WITH_DELIVERY_CONTACT_SELECT =
    "*,delivery:profiles!delivery_id(full_name,phone)"

/** Embeds the customer's phone, so the seller can call about an order without looking it up elsewhere. */
private const val ORDER_WITH_CUSTOMER_CONTACT_SELECT =
    "*,customer:profiles!customer_id(full_name,phone)"

class OrderRepository {

    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun createOrder(order: Order, items: List<OrderItem>): Result<String> {
        return try {
            val token = token()
            // Writable columns only: serializing Order would also send id="", created_at=""
            // and the nested `items` list, none of which the orders table accepts.
            val orderBody = buildJsonObject {
                put("customer_id", order.customerId)
                put("seller_id", order.sellerId)
                order.deliveryId?.let { put("delivery_id", it) }
                put("status", order.status)
                put("total_amount", order.totalAmount)
                put("delivery_fee", order.deliveryFee)
                put("delivery_address", order.deliveryAddress)
                order.deliveryLatitude?.let { put("delivery_latitude", it) }
                order.deliveryLongitude?.let { put("delivery_longitude", it) }
                put("payment_method", order.paymentMethod)
                put("wallet_used", order.walletUsed)
                order.paymentId?.let { put("payment_id", it) }
            }.toString()

            val created = SupabaseService.insert("orders", orderBody, token)
            val result = json.decodeFromString(Order.serializer(), created.toString())
            if (result.id.isBlank()) throw Exception("Order was created but no ID was returned.")

            items.forEach { item ->
                val itemBody = buildJsonObject {
                    put("order_id", result.id)
                    put("product_id", item.productId)
                    put("quantity", item.quantity)
                    put("price_at_purchase", item.priceAtPurchase)
                }.toString()
                SupabaseService.insert("order_items", itemBody, token)
            }

            Result.success(result.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeOrders(list: List<JsonObject>): List<Order> =
        list.map { json.decodeFromString(Order.serializer(), it.toString()) }
            .sortedByDescending { it.createdAt }

    suspend fun getCustomerOrders(customerId: String): Result<List<Order>> {
        return try {
            Result.success(
                decodeOrders(
                    SupabaseService.select(
                        "orders",
                        token(),
                        mapOf("customer_id" to customerId),
                        select = ORDER_WITH_DELIVERY_CONTACT_SELECT
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSellerOrders(sellerId: String): Result<List<Order>> {
        return try {
            Result.success(
                decodeOrders(
                    SupabaseService.select(
                        "orders",
                        token(),
                        mapOf("seller_id" to sellerId),
                        select = ORDER_WITH_CUSTOMER_CONTACT_SELECT
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeliveryOrders(deliveryId: String): Result<List<Order>> {
        return try {
            Result.success(
                decodeOrders(
                    SupabaseService.select(
                        "orders",
                        token(),
                        mapOf("delivery_id" to deliveryId),
                        select = ORDER_WITH_SELLER_SELECT
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllOrders(): Result<List<Order>> {
        return try {
            Result.success(decodeOrders(SupabaseService.selectAll("orders", token())))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrder(orderId: String): Result<Order?> {
        return try {
            val list = SupabaseService.select(
                "orders",
                token(),
                mapOf("id" to orderId),
                select = ORDER_WITH_DELIVERY_CONTACT_SELECT
            )
            val order = list.firstOrNull()?.let { json.decodeFromString(Order.serializer(), it.toString()) }
            Result.success(order)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String): Result<Unit> {
        return try {
            // Built as JSON rather than interpolated into a string literal, so a value
            // containing a quote cannot break out and corrupt the request body.
            val body = buildJsonObject { put("status", status) }.toString()
            SupabaseService.update("orders", orderId, body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The pool of orders a seller has packed but no rider has picked up yet —
     * any delivery partner may claim one. Ordinary select(): the RLS policy
     * that lets a delivery-role user see an unclaimed ready_for_pickup row
     * already excludes everyone else, so there is nothing to filter here.
     */
    suspend fun getAvailableOrders(): Result<List<Order>> {
        return try {
            Result.success(
                decodeOrders(
                    SupabaseService.select(
                        "orders",
                        token(),
                        mapOf("status" to OrderStatus.READY_FOR_PICKUP.value),
                        select = ORDER_WITH_SELLER_SELECT
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * A rider taps Accept on a pool order. Filtered on delivery_id being still
     * null, so this is the atomic half of claiming — see
     * SupabaseService.updateIfColumnNull. Returns false rather than failing
     * when nothing matched: that means another rider's claim landed first, not
     * that anything went wrong.
     */
    suspend fun claimOrder(orderId: String, deliveryId: String): Result<Boolean> {
        return try {
            val body = buildJsonObject {
                put("delivery_id", deliveryId)
                put("status", OrderStatus.OUT_FOR_DELIVERY.value)
            }.toString()
            val rows = SupabaseService.updateIfColumnNull(
                table = "orders",
                id = orderId,
                nullColumn = "delivery_id",
                body = body,
                token = token()
            )
            Result.success(rows.isNotEmpty())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrderItems(orderId: String): Result<List<OrderItem>> {
        return try {
            val list = SupabaseService.select(
                "order_items",
                token(),
                mapOf("order_id" to orderId),
                select = "*,product:products(*)"
            )
            Result.success(list.map { json.decodeFromString(OrderItem.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(orderId: String): Result<Unit> = updateOrderStatus(orderId, "cancelled")
}
