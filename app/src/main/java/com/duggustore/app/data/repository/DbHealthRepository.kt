package com.duggustore.app.data.repository

import com.duggustore.app.data.model.DbHealth
import com.duggustore.app.data.model.TableStat
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json

/**
 * Reads the database health figures behind the admin panel's Database tab.
 *
 * Both calls are read-only RPCs (see supabase_admin_stats.sql) that check the
 * caller is an admin server side, so nothing here needs a privileged key.
 */
class DbHealthRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun token() = SessionManager.getAccessToken()

    /** Row count and last write time for every table in the public schema. */
    suspend fun getTableStats(): Result<List<TableStat>> {
        return try {
            val body = SupabaseService.rpc("admin_table_stats", "{}", token())
            Result.success(json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(TableStat.serializer()), body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Headline counters: signups, orders, revenue, and the queues needing attention. */
    suspend fun getHealth(): Result<DbHealth> {
        return try {
            val body = SupabaseService.rpc("admin_db_health", "{}", token())
            Result.success(json.decodeFromString(DbHealth.serializer(), body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
