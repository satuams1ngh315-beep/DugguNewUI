package com.duggustore.app.data.repository

import com.duggustore.app.data.model.Campaign
import com.duggustore.app.data.remote.DateUtil
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Seasonal pushes for the home rail. The live-window check (is_active and
 * now() between starts_at/ends_at) lives entirely in the table's RLS policy,
 * so a plain unfiltered select already returns only what a customer should
 * see; an admin session's own policy additionally grants it every row,
 * running or not, which is what the admin screens rely on.
 */
class CampaignRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun getRunningCampaigns(): Result<List<Campaign>> {
        return try {
            val rows = SupabaseService.select("campaigns", token())
            Result.success(rows.map { json.decodeFromJsonElement(Campaign.serializer(), it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Admin-only in practice — RLS only lets an admin session see every campaign, running or not. */
    suspend fun getAllCampaigns(): Result<List<Campaign>> {
        return try {
            val rows = SupabaseService.selectAll("campaigns", token())
            Result.success(rows.map { json.decodeFromJsonElement(Campaign.serializer(), it) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun body(campaign: Campaign, durationDays: Int): JsonObject = buildJsonObject {
        put("label", campaign.label)
        put("tint_hex", campaign.tintHex)
        campaign.categoryId?.let { put("category_id", it) } ?: put("category_id", JsonNull)
        put("cta_label", campaign.ctaLabel)
        put("starts_at", DateUtil.isoNow())
        put("ends_at", DateUtil.isoPlusDays(durationDays))
        put("is_active", campaign.isActive)
    }

    /** [durationDays] restarts the window from now — there's no separate "extend" action, just running it again. */
    suspend fun createCampaign(campaign: Campaign, durationDays: Int): Result<Unit> {
        return try {
            SupabaseService.insert("campaigns", body(campaign, durationDays).toString(), token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCampaign(campaign: Campaign, durationDays: Int): Result<Unit> {
        return try {
            SupabaseService.update("campaigns", campaign.id, body(campaign, durationDays).toString(), token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setActive(campaign: Campaign, isActive: Boolean): Result<Unit> {
        return try {
            val body = buildJsonObject { put("is_active", isActive) }.toString()
            SupabaseService.update("campaigns", campaign.id, body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCampaign(id: String): Result<Unit> {
        return try {
            SupabaseService.delete("campaigns", id, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
