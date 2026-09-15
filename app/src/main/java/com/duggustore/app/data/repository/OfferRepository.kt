package com.duggustore.app.data.repository

import com.duggustore.app.data.model.Coupon
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Calendar

/**
 * The offers shown on the home carousel.
 *
 * These are the store's real coupons rather than pictures of offers, so the code
 * on the card is one the customer can actually type into the cart.
 */
class OfferRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun getOffers(): Result<List<Coupon>> {
        return try {
            val rows = SupabaseService.select(
                "coupons",
                token(),
                mapOf("is_active" to "true")
            )
            val coupons = rows.map { json.decodeFromJsonElement(Coupon.serializer(), it) }
            Result.success(rotateForToday(coupons))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Same offers, different one in front each day.
     *
     * A fixed order means whoever opens the app sees the same card at the top
     * every morning and stops reading it. Rotating by day of year moves a
     * different offer to the front without needing anything scheduled server
     * side, and keeps the order stable for the whole of that day.
     */
    private fun rotateForToday(coupons: List<Coupon>): List<Coupon> {
        if (coupons.size < 2) return coupons
        // Sorted first so the rotation starts from the same place on every
        // device, whatever order PostgREST happened to return.
        val stable = coupons.sortedBy { it.code }
        val shift = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % stable.size
        return stable.drop(shift) + stable.take(shift)
    }

    /** Admin-only in practice — RLS only lets an admin session see inactive coupons too. */
    suspend fun getAllCoupons(): Result<List<Coupon>> {
        return try {
            val rows = SupabaseService.selectAll("coupons", token())
            Result.success(rows.map { json.decodeFromJsonElement(Coupon.serializer(), it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun body(coupon: Coupon): JsonObject = buildJsonObject {
        put("code", coupon.code)
        put("title", coupon.title)
        put("description", coupon.description)
        put("discount_percent", coupon.discountPercent)
        put("max_discount", coupon.maxDiscount)
        put("min_order_value", coupon.minOrderValue)
        put("expiry_label", coupon.expiryLabel)
        put("is_active", coupon.isActive)
    }

    suspend fun createCoupon(coupon: Coupon): Result<Unit> {
        return try {
            SupabaseService.insert("coupons", body(coupon).toString(), token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCoupon(coupon: Coupon): Result<Unit> {
        return try {
            SupabaseService.update("coupons", coupon.id, body(coupon).toString(), token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCoupon(id: String): Result<Unit> {
        return try {
            SupabaseService.delete("coupons", id, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
