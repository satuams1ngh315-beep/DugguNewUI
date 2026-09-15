package com.duggustore.app.data.repository

import com.duggustore.app.data.model.Address
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class AddressRepository {

    private fun token(): String? = SessionManager.getAccessToken()

    /** Writable columns only — the database generates id and created_at. */
    private fun body(address: Address): JsonObject = buildJsonObject {
        put("user_id", address.userId)
        put("label", address.label)
        put("full_address", address.fullAddress)
        put("latitude", address.latitude)
        put("longitude", address.longitude)
        put("is_default", address.isDefault)
    }

    suspend fun getAddresses(userId: String): Result<List<Address>> {
        return try {
            val list = SupabaseService.select("addresses", token(), mapOf("user_id" to userId))
            val addresses = list.map { json.decodeFromString(Address.serializer(), it.toString()) }
            // Default first, so the checkout screen can preselect it.
            Result.success(addresses.sortedByDescending { it.isDefault })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addAddress(address: Address): Result<Unit> {
        return try {
            if (address.isDefault) clearDefault(address.userId)
            SupabaseService.insert("addresses", body(address).toString(), token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAddress(address: Address): Result<Unit> {
        return try {
            if (address.isDefault) clearDefault(address.userId, exceptId = address.id)
            SupabaseService.update("addresses", address.id, body(address).toString(), token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAddress(addressId: String): Result<Unit> {
        return try {
            SupabaseService.delete("addresses", addressId, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setDefault(userId: String, addressId: String): Result<Unit> {
        return try {
            clearDefault(userId)
            val body = buildJsonObject { put("is_default", true) }.toString()
            SupabaseService.update("addresses", addressId, body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Only one address may be the default, and the table has no constraint enforcing
     * it, so the previous default is cleared before a new one is set.
     */
    private suspend fun clearDefault(userId: String, exceptId: String? = null) {
        try {
            val current = SupabaseService.select("addresses", token(), mapOf("user_id" to userId))
                .map { json.decodeFromString(Address.serializer(), it.toString()) }
                .filter { it.isDefault && it.id != exceptId }
            val body = buildJsonObject { put("is_default", false) }.toString()
            current.forEach { SupabaseService.update("addresses", it.id, body, token()) }
        } catch (_: Exception) {
            // Best effort: a stale second default is not worth failing the save for.
        }
    }
}
