package com.duggustore.app.data.repository

import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The FCM tokens push notifications actually get sent to. Registered after
 * login and on token rotation; removed on sign-out so a device that's no
 * longer signed in as this user stops receiving their pushes.
 */
class DeviceTokenRepository {
    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun registerToken(userId: String, fcmToken: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("user_id", userId)
                put("token", fcmToken)
                put("platform", "android")
            }.toString()
            SupabaseService.upsert("device_tokens", body, onConflict = "user_id,token", token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Best-effort on sign-out — scoped by RLS to whichever user the current session belongs to. */
    suspend fun deleteToken(fcmToken: String): Result<Unit> {
        return try {
            SupabaseService.deleteWhere("device_tokens", "token", fcmToken, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
