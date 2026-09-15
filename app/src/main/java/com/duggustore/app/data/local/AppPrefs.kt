package com.duggustore.app.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * App settings that outlive a session. Deliberately a separate store from the
 * auth session, whose clearSession() wipes everything in its own file — the
 * chosen language should survive signing out.
 */
object AppPrefs {
    private const val PREF_NAME = "duggu_store_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_RECENT_SEARCHES = "recent_searches"
    // Newline rather than a comma — a search term could contain one, but the
    // single-line search field it comes from can never produce a newline.
    private const val RECENT_SEARCH_SEPARATOR = "\n"
    private const val MAX_RECENT_SEARCHES = 8
    private const val KEY_WELCOME_SEEN = "welcome_seen"
    private const val KEY_ORDER_ALERTS = "order_voice_alerts"
    private const val KEY_REORDER_REMINDERS = "reorder_reminders"
    // "orderId|dueAtEpochMillis" pairs, one per line — there's no server-side
    // scheduler, so due reminders are just checked against the clock whenever
    // the orders list is opened rather than firing a background notification.
    private const val REMINDER_ENTRY_SEPARATOR = "\n"
    private const val REMINDER_FIELD_SEPARATOR = "|"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Null means "follow the device", which is the default.
     *
     * Takes a context rather than using the cached instance because
     * attachBaseContext runs before Application.onCreate, so init() has not
     * happened yet at the point the locale has to be applied.
     */
    fun language(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)

    fun setLanguage(context: Context, tag: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, tag)
            apply()
        }
    }

    /**
     * Whether the intro slides have been through once. Kept here rather than
     * with the session so signing out doesn't replay them at someone who has
     * already used the app.
     */
    fun hasSeenWelcome(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WELCOME_SEEN, false)

    fun setWelcomeSeen(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WELCOME_SEEN, true)
            .apply()
    }

    /**
     * Whether new orders are read out loud to the seller. On by default — a
     * seller missing an order is the whole problem it exists to solve — but it
     * plays on the alarm stream, so it has to be switchable.
     */
    fun isOrderAlertEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ORDER_ALERTS, true)

    fun setOrderAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ORDER_ALERTS, enabled)
            .apply()
    }

    /** Most recent search first, capped at [MAX_RECENT_SEARCHES]. */
    fun recentSearches(context: Context): List<String> =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_SEARCHES, null)
            ?.split(RECENT_SEARCH_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** Moves [term] to the front, de-duplicating case-insensitively. */
    fun addRecentSearch(context: Context, term: String) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        val updated = (listOf(trimmed) + recentSearches(context).filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(MAX_RECENT_SEARCHES)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECENT_SEARCHES, updated.joinToString(RECENT_SEARCH_SEPARATOR))
            .apply()
    }

    /** Drops one term, so the history list can be pruned a row at a time. */
    fun removeRecentSearch(context: Context, term: String) {
        val remaining = recentSearches(context).filterNot { it.equals(term.trim(), ignoreCase = true) }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        if (remaining.isEmpty()) {
            prefs.remove(KEY_RECENT_SEARCHES)
        } else {
            prefs.putString(KEY_RECENT_SEARCHES, remaining.joinToString(RECENT_SEARCH_SEPARATOR))
        }
        prefs.apply()
    }

    fun clearRecentSearches(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_RECENT_SEARCHES)
            .apply()
    }

    private fun reminderMap(context: Context): Map<String, Long> =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REORDER_REMINDERS, null)
            ?.split(REMINDER_ENTRY_SEPARATOR)
            ?.mapNotNull { entry ->
                val parts = entry.split(REMINDER_FIELD_SEPARATOR)
                val dueAt = parts.getOrNull(1)?.toLongOrNull()
                if (parts.size == 2 && dueAt != null) parts[0] to dueAt else null
            }
            ?.toMap()
            ?: emptyMap()

    private fun saveReminderMap(context: Context, map: Map<String, Long>) {
        val serialized = map.entries.joinToString(REMINDER_ENTRY_SEPARATOR) { (orderId, dueAt) ->
            "$orderId$REMINDER_FIELD_SEPARATOR$dueAt"
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_REORDER_REMINDERS, serialized)
            .apply()
    }

    /** Schedules a "time to reorder?" nudge for [orderId], [days] from now. */
    fun setReorderReminder(context: Context, orderId: String, days: Int) {
        val dueAt = System.currentTimeMillis() + days * 24L * 60 * 60 * 1000
        saveReminderMap(context, reminderMap(context) + (orderId to dueAt))
    }

    fun hasReorderReminder(context: Context, orderId: String): Boolean =
        reminderMap(context).containsKey(orderId)

    /** Order ids whose reminder time has passed — surfaced as a nudge next time the orders list opens. */
    fun dueReorderReminders(context: Context): List<String> {
        val now = System.currentTimeMillis()
        return reminderMap(context).filterValues { it <= now }.keys.toList()
    }

    fun clearReorderReminder(context: Context, orderId: String) {
        saveReminderMap(context, reminderMap(context) - orderId)
    }
}
