package com.duggustore.app.data.repository

import com.duggustore.app.data.model.DeliveryTracking
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The rider's live position for an order.
 *
 * delivery_tracking has existed since the first schema with no repository and no
 * caller. Its policies already say the rider may write their own row and the
 * customer may read the row for an order that is theirs, so nothing here has to
 * be trusted by the client — the database decides.
 */
class TrackingRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = SessionManager.getAccessToken()

    /** Called by the rider. One row per order, overwritten on every update. */
    suspend fun publishLocation(
        orderId: String,
        deliveryId: String,
        latitude: Double,
        longitude: Double,
        status: String
    ): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("order_id", orderId)
                put("delivery_id", deliveryId)
                put("latitude", latitude)
                put("longitude", longitude)
                put("status", status)
                // The column defaults to now() on insert only, so an overwrite has
                // to carry the time or the row would keep its original stamp and
                // the customer could not tell a stale fix from a fresh one.
                put("updated_at", nowIso())
            }
            SupabaseService.upsert(
                table = "delivery_tracking",
                body = body.toString(),
                onConflict = "order_id",
                token = token()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Called by the customer while watching an order. */
    suspend fun getTracking(orderId: String): Result<DeliveryTracking?> {
        return try {
            val rows = SupabaseService.select(
                "delivery_tracking",
                token(),
                mapOf("order_id" to orderId)
            )
            Result.success(rows.firstOrNull()?.let { json.decodeFromJsonElement(DeliveryTracking.serializer(), it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * java.time needs API 26 and this module has no core library desugaring, so
     * the timestamp is formatted the old way to keep working on minSdk 24.
     */
    private fun nowIso(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
