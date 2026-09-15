package com.duggustore.app.data.repository

import com.duggustore.app.data.model.HomeSection
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The home page's browse layout: admin-curated sections, each a title and an
 * ordered set of categories.
 *
 * Customers read it through the home_layout RPC, which assembles the whole
 * page — sections, their categories, and each category's live product count
 * and preview photos — in one round trip. Doing that here would be a query
 * per category on every home load.
 *
 * The admin side writes the two tables directly; RLS there is admin-only, so
 * these calls simply fail for anyone else rather than needing their own gate.
 */
class HomeLayoutRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = SessionManager.getAccessToken()

    /** The whole browse layout as the customer sees it. */
    suspend fun getLayout(): Result<List<HomeSection>> {
        return try {
            val body = SupabaseService.rpc("home_layout", token = token())
            Result.success(
                json.decodeFromString(ListSerializer(HomeSection.serializer()), body)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Every section including the hidden ones, without their categories —
     * the admin list. RLS returns the inactive rows only to an admin session.
     */
    suspend fun getAllSections(): Result<List<HomeSection>> {
        return try {
            val rows = SupabaseService.selectAll("home_sections", token())
            Result.success(
                rows.map { json.decodeFromJsonElement(HomeSection.serializer(), it) }
                    .sortedBy { it.sortOrder }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Every section's category ids at once, keyed by section and each list in
     * display order — one call rather than one per section, since the admin
     * screen needs them all to render its list.
     */
    suspend fun getAllSectionCategories(): Result<Map<String, List<String>>> {
        return try {
            val rows = SupabaseService.selectAll("home_section_categories", token())
            val grouped = rows
                .sortedBy { it["sort_order"]?.jsonPrimitive?.intOrNull ?: 0 }
                .mapNotNull { row ->
                    val section = row["section_id"]?.jsonPrimitive?.contentOrNull
                    val category = row["category_id"]?.jsonPrimitive?.contentOrNull
                    if (section != null && category != null) section to category else null
                }
                .groupBy({ it.first }, { it.second })
            Result.success(grouped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSection(title: String, layout: String, sortOrder: Int): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("title", title)
                put("layout", layout)
                put("sort_order", sortOrder)
            }.toString()
            SupabaseService.insert("home_sections", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSection(
        id: String,
        title: String,
        layout: String,
        sortOrder: Int
    ): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("title", title)
                put("layout", layout)
                put("sort_order", sortOrder)
            }.toString()
            SupabaseService.update("home_sections", id, body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setSectionActive(id: String, isActive: Boolean): Result<Unit> {
        return try {
            val body = buildJsonObject { put("is_active", isActive) }.toString()
            SupabaseService.update("home_sections", id, body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cascades to the section's category rows via the foreign key. */
    suspend fun deleteSection(id: String): Result<Unit> {
        return try {
            SupabaseService.delete("home_sections", id, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Replaces a section's category list wholesale rather than diffing it —
     * the admin picker hands back the full set, and the order is the order
     * they appear in [categoryIds].
     */
    suspend fun setSectionCategories(sectionId: String, categoryIds: List<String>): Result<Unit> {
        return try {
            SupabaseService.deleteWhere(
                table = "home_section_categories",
                column = "section_id",
                value = sectionId,
                token = token()
            )
            categoryIds.forEachIndexed { index, categoryId ->
                val body = buildJsonObject {
                    put("section_id", sectionId)
                    put("category_id", categoryId)
                    put("sort_order", index + 1)
                }.toString()
                SupabaseService.insert("home_section_categories", body, token())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
