package com.duggustore.app.data.repository

import com.duggustore.app.data.model.OrderIssue
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class OrderIssueRepository {
    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun reportIssue(
        orderId: String,
        productId: String?,
        userId: String,
        reason: String,
        description: String
    ): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("order_id", orderId)
                productId?.let { put("product_id", it) }
                put("user_id", userId)
                put("reason", reason)
                put("description", description)
            }.toString()
            SupabaseService.insert("order_issues", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyIssues(userId: String): Result<List<OrderIssue>> {
        return try {
            val list = SupabaseService.select("order_issues", token(), mapOf("user_id" to userId))
            Result.success(list.map { json.decodeFromString(OrderIssue.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getIssuesForOrder(userId: String, orderId: String): Result<List<OrderIssue>> {
        return try {
            val list = SupabaseService.select(
                "order_issues",
                token(),
                mapOf("user_id" to userId, "order_id" to orderId)
            )
            Result.success(list.map { json.decodeFromString(OrderIssue.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** RLS already narrows the rows to the caller's own orders (seller) or everything (admin). */
    suspend fun getIssuesForReview(): Result<List<OrderIssue>> {
        return try {
            Result.success(
                SupabaseService.selectAll("order_issues", token())
                    .map { json.decodeFromString(OrderIssue.serializer(), it.toString()) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Goes through the resolve_order_issue Postgres function rather than a
     * plain UPDATE: it checks the caller is the order's seller (or an admin)
     * and, only if approved, credits the customer's wallet in the same
     * transaction — a customer can never grant their own refund this way.
     */
    suspend fun resolveIssue(issueId: String, approve: Boolean, refundAmount: Int, note: String = ""): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("p_issue_id", issueId)
                put("p_approve", approve)
                put("p_refund_amount", refundAmount)
                put("p_note", note)
            }.toString()
            SupabaseService.rpc("resolve_order_issue", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
