package com.duggustore.app.data.repository

import com.duggustore.app.data.model.SponsoredSlot
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Seller-paid placements on the home rail, each promoting one specific
 * product the seller already lists. Same live-window-in-RLS trick as
 * campaigns: a plain select only ever returns slots that are APPROVED and
 * currently running, so [getLiveSlots] needs no date filtering of its own.
 * Collecting the quoted fee happens outside the app for now — approving a
 * request here is the only gate that puts it live.
 */
class SponsoredSlotRepository {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Nominal on purpose — the app is new and this is meant to be an easy
     * yes, not a real ad-spend decision. Mirrors the table's own generated
     * column (duration_days * 5) purely so the UI can preview a total before
     * submitting; the row's real fee_amount always comes from the database,
     * never from what this — or any other — client sends.
     */
    private val feePerDayRupees = 5

    private fun token(): String? = SessionManager.getAccessToken()

    fun quoteFee(durationDays: Int): Int = feePerDayRupees * durationDays

    private val withProduct = "*,product:products(*)"

    suspend fun getLiveSlots(): Result<List<SponsoredSlot>> {
        return try {
            val rows = SupabaseService.select("sponsored_slots", token(), select = withProduct)
            Result.success(rows.map { json.decodeFromJsonElement(SponsoredSlot.serializer(), it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** A seller's own requests, whatever their status — RLS lets them see only their own rows here. */
    suspend fun getMySlots(sellerId: String): Result<List<SponsoredSlot>> {
        return try {
            val rows = SupabaseService.select("sponsored_slots", token(), mapOf("seller_id" to sellerId), withProduct)
            Result.success(rows.map { json.decodeFromJsonElement(SponsoredSlot.serializer(), it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * [productId] must be one of this seller's own products — RLS rejects
     * anything else. The fee is quoted and frozen here rather than computed
     * again at review time, so it can't drift out from under an approved
     * request if the rate changes later. starts_at/ends_at are left unset —
     * they're only stamped once an admin approves, via review_sponsored_slot.
     */
    suspend fun requestSlot(sellerId: String, productId: String, durationDays: Int, note: String = ""): Result<Unit> {
        return try {
            // fee_amount is a generated column — sending it would be rejected.
            val body = buildJsonObject {
                put("seller_id", sellerId)
                put("product_id", productId)
                put("headline", note)
                put("duration_days", durationDays)
                put("status", "PENDING")
            }.toString()
            SupabaseService.insert("sponsored_slots", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Admin-only in practice — RLS only lets an admin session see every seller's requests. */
    suspend fun getAllSlots(): Result<List<SponsoredSlot>> {
        return try {
            val rows = SupabaseService.selectAll("sponsored_slots", token(), withProduct)
            Result.success(rows.map { json.decodeFromJsonElement(SponsoredSlot.serializer(), it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reviewSlot(slotId: String, approve: Boolean, rejectionReason: String = ""): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("p_slot_id", slotId)
                put("p_approve", approve)
                put("p_rejection_reason", rejectionReason)
            }.toString()
            SupabaseService.rpc("review_sponsored_slot", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
