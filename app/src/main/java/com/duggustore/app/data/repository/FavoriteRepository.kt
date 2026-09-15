package com.duggustore.app.data.repository

import com.duggustore.app.data.model.Favorite
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseException
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class FavoriteRepository {

    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun getFavorites(customerId: String): Result<List<Favorite>> {
        return try {
            val list = SupabaseService.select("favorites", token(), mapOf("customer_id" to customerId))
            Result.success(list.map { json.decodeFromString(Favorite.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addToFavorites(customerId: String, productId: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("customer_id", customerId)
                put("product_id", productId)
            }.toString()
            SupabaseService.insert("favorites", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            // favorites has UNIQUE(customer_id, product_id); double-tapping the heart is
            // not an error the user needs to see.
            if ((e as? SupabaseException)?.statusCode == 409) Result.success(Unit)
            else Result.failure(e)
        }
    }

    suspend fun removeFromFavorites(customerId: String, productId: String): Result<Unit> {
        return try {
            val token = token()
            val existing = SupabaseService.select(
                "favorites",
                token,
                mapOf("customer_id" to customerId, "product_id" to productId)
            ).firstOrNull()?.let { json.decodeFromString(Favorite.serializer(), it.toString()) }

            existing?.let { SupabaseService.delete("favorites", it.id, token) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isFavorite(customerId: String, productId: String): Boolean {
        return try {
            SupabaseService.select(
                "favorites",
                token(),
                mapOf("customer_id" to customerId, "product_id" to productId)
            ).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
