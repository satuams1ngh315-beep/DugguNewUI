package com.duggustore.app.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Swaps an expired access token for a fresh one.
 *
 * Supabase access tokens only last about an hour, so any screen left open past
 * that point (or reopened from the background the next day) starts getting
 * 401 "JWT expired" back on every call. [SupabaseService] consults this on a
 * 401 and replays the request, which keeps the refresh in one place instead of
 * asking all ~27 call sites to handle it.
 */
internal object SessionRefresher {

    /**
     * A screen typically fires several requests at once, so an expired token
     * produces a burst of simultaneous 401s. The lock means the first one
     * refreshes and the rest wait for and reuse that result, rather than each
     * spending the refresh token separately — Supabase rotates it on use, so
     * concurrent refreshes would invalidate each other and sign the user out.
     */
    private val mutex = Mutex()

    /**
     * Returns a usable access token, or null when the session is truly gone
     * (in which case the stored session has been cleared).
     *
     * @param staleToken the token that just failed, so a caller that lost the
     *   race can tell "someone already refreshed for me" from "still stale".
     */
    suspend fun refresh(staleToken: String?): String? = mutex.withLock {
        // Someone else refreshed while this call waited for the lock.
        val current = SessionManager.getAccessToken()
        if (!current.isNullOrBlank() && current != staleToken) return@withLock current

        val refreshToken = SessionManager.getRefreshToken()?.takeIf { it.isNotBlank() }
            ?: run {
                SessionManager.clearSession()
                return@withLock null
            }

        try {
            val resp = SupabaseService.refreshSession(refreshToken)
            val accessToken = str(resp, "access_token")
                ?: str(resp["session"] as? JsonObject, "access_token")
                ?: return@withLock null
            val newRefresh = str(resp, "refresh_token")
                ?: str(resp["session"] as? JsonObject, "refresh_token")
                ?: refreshToken
            val userId = (resp["user"] as? JsonObject)?.let { str(it, "id") }
                ?: SessionManager.getUserId()
                ?: return@withLock null

            SessionManager.saveSession(
                accessToken,
                newRefresh,
                userId,
                SessionManager.getEmail().orEmpty()
            )
            accessToken
        } catch (e: SupabaseException) {
            // A rejected refresh token means the session is genuinely over, so
            // clear it and let the app fall back to sign-in. A network blip is
            // not a logout though — keep the session and let the caller retry.
            if (e.statusCode in 400..499) SessionManager.clearSession()
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun str(obj: JsonObject?, key: String): String? =
        (obj?.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
