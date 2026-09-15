package com.duggustore.app.data.repository

import com.duggustore.app.data.model.Review
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class ReviewRepository {

    private fun token(): String? = SessionManager.getAccessToken()

    /** Every review left for a product, newest first — shown on its detail page. */
    suspend fun getReviewsForProduct(productId: String): Result<List<Review>> {
        return try {
            val rows = SupabaseService.select("reviews", token(), mapOf("product_id" to productId))
            Result.success(
                rows.map { json.decodeFromString(Review.serializer(), it.toString()) }
                    .sortedByDescending { it.createdAt }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * This customer's own reviews on this order, so the "rate this item" row
     * can show what they already rated instead of asking again from scratch.
     */
    suspend fun getMyReviewsForOrder(userId: String, orderId: String): Result<List<Review>> {
        return try {
            val rows = SupabaseService.select(
                "reviews",
                token(),
                mapOf("user_id" to userId, "order_id" to orderId)
            )
            Result.success(rows.map { json.decodeFromString(Review.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upserts on (order_id, product_id, user_id) — the same item on the same
     * order rated twice overwrites the first rating rather than duplicating it.
     */
    suspend fun submitReview(
        userId: String,
        orderId: String,
        productId: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("user_id", userId)
                put("order_id", orderId)
                put("product_id", productId)
                put("rating", rating)
                put("comment", comment)
            }.toString()
            SupabaseService.upsert("reviews", body, onConflict = "order_id,product_id,user_id", token = token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
