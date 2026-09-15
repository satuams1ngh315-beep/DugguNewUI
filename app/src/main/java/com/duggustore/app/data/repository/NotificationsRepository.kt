package com.duggustore.app.data.repository

import com.duggustore.app.data.model.StoreNotification
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Raw DB row from the `notifications` table. */
@Serializable
data class DbNotification(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val type: String = "ORDER",
    val title: String = "",
    val message: String = "",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("order_id") val orderId: String? = null
)

/**
 * Reads and manages the `notifications` table, which is populated server-side
 * by the `trg_order_status_notify` trigger whenever an order's status changes.
 *
 * The existing [StoreNotification] / [NotificationsScreen] contract is preserved:
 * DB rows are mapped into the same shape so no UI changes are required.
 */
class NotificationsRepository {

    private fun token(): String? = SessionManager.getAccessToken()

    /**
     * Every row in this table is scoped to a user by RLS (`auth.uid() =
     * user_id`), so a request without an access token is not merely
     * unauthorised — it is anonymous, matches nothing, and comes back 200
     * with an empty body. Sending one looks like success to the caller and
     * writes nothing, which is the worst of both.
     *
     * A stored session can go missing mid-run: the access token lasts about
     * an hour, and a refresh that fails clears the session while the signed-in
     * user is still on screen. So fail loudly here rather than firing an
     * anonymous write into the void.
     */
    private fun requireToken(): String =
        token()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Signed-out session — sign in again to sync notifications")

    /** Returns all notifications for [userId], newest first. */
    suspend fun getNotifications(userId: String): Result<List<DbNotification>> {
        return try {
            // PostgREST ordering: append order query param manually because
            // SupabaseService.select() only supports eq-filters.
            val rows = SupabaseService.select(
                table = "notifications",
                token = token(),
                params = mapOf("user_id" to userId),
                select = "*&order=created_at.desc"
            )
            Result.success(rows.map {
                json.decodeFromString(DbNotification.serializer(), it.toString())
            })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readBody() = buildJsonObject { put("is_read", JsonPrimitive(true)) }.toString()

    /**
     * Marks a single notification row as read, reporting how many rows the
     * server actually changed.
     *
     * A PATCH that matches nothing still comes back 200 with an empty body,
     * so "no exception" is not the same as "written". That happens for real:
     * RLS on this table is `auth.uid() = user_id`, so an expired or missing
     * access token leaves the request anonymous, matches zero rows, and looks
     * like success to a caller that only checks for a thrown error.
     */
    suspend fun markRead(notificationId: String): Result<Int> {
        return try {
            val changed = SupabaseService.update(
                "notifications",
                notificationId,
                readBody(),
                requireToken()
            )
            Result.success(changed.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks every unread notification for [userId] as read in one PATCH, and
     * returns how many rows that changed.
     *
     * Filtered on is_read as well as user_id so the write only touches rows
     * that need it — which also makes the returned count mean something: zero
     * back when the caller knew there were unread rows is a failed write, not
     * a no-op.
     */
    suspend fun markAllRead(userId: String): Result<Int> {
        return try {
            val changed = SupabaseService.updateWhereAll(
                table = "notifications",
                filters = mapOf("user_id" to userId, "is_read" to "false"),
                body = readBody(),
                token = requireToken()
            )
            Result.success(changed.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- mapping to the shared StoreNotification model ----------------

    fun DbNotification.toStoreNotification(): StoreNotification {
        val kind = when {
            title.contains("confirmed", ignoreCase = true) -> StoreNotification.Kind.Confirmed
            title.contains("prepared", ignoreCase = true) ||
                title.contains("preparing", ignoreCase = true) -> StoreNotification.Kind.Preparing
            title.contains("pickup", ignoreCase = true) -> StoreNotification.Kind.ReadyForPickup
            title.contains("delivery", ignoreCase = true) ||
                title.contains("way", ignoreCase = true) -> StoreNotification.Kind.OutForDelivery
            title.contains("delivered", ignoreCase = true) -> StoreNotification.Kind.Delivered
            title.contains("cancelled", ignoreCase = true) -> StoreNotification.Kind.Cancelled
            else -> StoreNotification.Kind.Placed
        }
        return StoreNotification(
            id = id,
            title = title,
            body = message,
            timestamp = createdAt,
            orderId = orderId.orEmpty(),
            kind = kind
        )
    }
}
