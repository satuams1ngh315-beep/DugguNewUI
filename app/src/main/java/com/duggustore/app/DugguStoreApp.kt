package com.duggustore.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.duggustore.app.data.local.AppPrefs
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.platform.CrashReporting
import com.duggustore.app.platform.PushNotifications
import org.osmdroid.config.Configuration
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DugguStoreApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
        AppPrefs.init(this)
        installCrashLogger()
        PushNotifications.ensureChannel(this)

        // osmdroid's tile server rejects requests with no identifying user
        // agent, and defaults to writing its cache somewhere that needs a
        // storage permission this app doesn't otherwise ask for — the app's
        // own private cache dir needs neither.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles").apply { mkdirs() }
        }
    }

    /**
     * Product and category photos are plain URLs on duggustore.com, and that
     * server sends no Cache-Control or Expires header on any of them — only
     * Last-Modified. Coil's default ImageLoader has respectCacheHeaders =
     * true, so with nothing to go on it falls back to a heuristic freshness
     * window rather than caching outright — every screen that shows one of
     * these photos again (switching tabs, reopening the app, scrolling back
     * up) can end up re-fetching and re-decoding it instead of reading the
     * disk cache. These images don't change once uploaded, so disable that
     * and cache unconditionally.
     *
     * Crossfade is off on purpose: a 200ms fade on every card that enters
     * the viewport during a fling is extra GPU work on the scroll thread
     * and is what made the home feed stutter. The card already has a solid
     * white photo well, so a cache miss just fills in rather than animating.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(false)
            .allowHardware(true)
            .respectCacheHeaders(false)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()

    /**
     * A "sometimes the screen just goes blank, has to force-close" report is
     * otherwise unreproducible — there's no log to say what actually threw.
     * This writes the stack trace of any uncaught exception to a file under
     * app-specific external storage (no permission needed, and browsable
     * from a phone's Files app under Android/data/<package>/files/crash_logs)
     * before handing off to the previous handler, so a crash still behaves
     * exactly as it did — it's just no longer silent.
     */
    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Uploads to the Firebase console on next launch (best-effort,
            // never throws); the file below keeps an on-device copy too.
            CrashReporting.recordException(throwable)
            try {
                val dir = File(getExternalFilesDir(null), "crash_logs").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
                File(dir, "crash_$stamp.txt").writeText(
                    "Thread: ${thread.name}\n\n$trace"
                )
            } catch (_: Throwable) {
                // A failure while trying to log the original crash must never
                // replace it — fall through to the previous handler either way.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
