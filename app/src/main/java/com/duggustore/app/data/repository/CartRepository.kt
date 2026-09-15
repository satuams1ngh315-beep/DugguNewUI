package com.duggustore.app.data.repository

import com.duggustore.app.data.model.CartItem
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class CartRepository {

    // Every cart_items policy is scoped to `customer_id = auth.uid()`, so without the
    // user's token these calls carry only the anon key and match nothing at all.
    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun getCartItems(customerId: String): Result<List<CartItem>> {
        return try {
            // Embed the product: without it every CartItem.product is null, which made
            // the cart subtotal, savings and totals all compute as zero.
            val list = SupabaseService.select(
                "cart_items",
                token(),
                mapOf("customer_id" to customerId),
                select = "*,product:products(*)"
            )
            Result.success(list.map { json.decodeFromString(CartItem.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addToCart(customerId: String, productId: String, quantity: Int = 1): Result<Unit> {
        return try {
            val token = token()
            // Filter on both columns rather than pulling the whole cart down to search it.
            val existing = SupabaseService.select(
                "cart_items",
                token,
                mapOf("customer_id" to customerId, "product_id" to productId)
            ).firstOrNull()?.let { json.decodeFromString(CartItem.serializer(), it.toString()) }

            if (existing != null) {
                val body = buildJsonObject { put("quantity", existing.quantity + quantity) }.toString()
                SupabaseService.update("cart_items", existing.id, body, token)
            } else {
                // Only real columns: serializing CartItem would also send id="" (not a valid
                // uuid) and the nested `product` object, which is not a column at all.
                val body = buildJsonObject {
                    put("customer_id", customerId)
                    put("product_id", productId)
                    put("quantity", quantity)
                }.toString()
                SupabaseService.insert("cart_items", body, token)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateQuantity(itemId: String, quantity: Int): Result<Unit> {
        return try {
            if (quantity <= 0) {
                SupabaseService.delete("cart_items", itemId, token())
            } else {
                val body = buildJsonObject { put("quantity", quantity) }.toString()
                SupabaseService.update("cart_items", itemId, body, token())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFromCart(itemId: String): Result<Unit> {
        return try {
            SupabaseService.delete("cart_items", itemId, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearCart(customerId: String): Result<Unit> {
        return try {
            SupabaseService.deleteWhere("cart_items", "customer_id", customerId, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
