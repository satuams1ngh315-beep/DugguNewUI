package com.duggustore.app.data.remote

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * java.time needs API 26 and this module has no core library desugaring, so
 * timestamps are formatted the old way to keep working on minSdk 24 — same
 * approach TrackingRepository already uses for its own timestamps.
 */
object DateUtil {
    private fun format(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(date)
    }

    fun isoNow(): String = format(Date())

    /** [days] from now, for anything that runs "starting now, for N days". */
    fun isoPlusDays(days: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return format(calendar.time)
    }
}
