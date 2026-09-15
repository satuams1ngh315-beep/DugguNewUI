package com.duggustore.app.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Reads new orders out loud, the way a counter terminal calls one out rather
 * than just lighting up — a seller working the shop floor isn't watching the
 * screen, and a silent row appearing in a list is exactly what gets missed.
 *
 * Plays on the alarm stream on purpose: a shop is noisy, and the notification
 * stream is the one people turn right down, which would defeat the point.
 */
class OrderAnnouncer(context: Context) {

    private var engine: TextToSpeech? = null
    private var isReady = false
    private var isConfigured = false
    private var tone: ToneGenerator? = null

    init {
        // Application context: this outlives the composable that made it only
        // as far as release(), but holding the activity would still be a leak
        // for as long as the engine takes to initialise.
        engine = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
        }
        // Not fatal if the device won't hand one over — the speech is the part
        // that matters, the chime is just what makes someone look up first.
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME) }.getOrNull()
    }

    /**
     * Queued rather than interrupting: two orders landing together should be
     * read one after the other, not cut each other off.
     */
    fun announce(text: String) {
        val engine = engine ?: return
        if (!isReady) return
        // Set up on first use rather than in the init callback, which can fire
        // before the constructor has even returned the instance to assign.
        if (!isConfigured) {
            isConfigured = true
            runCatching {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            runCatching {
                // attachBaseContext sets the default locale from the language
                // the seller chose, so the announcement follows it wherever the
                // device actually has a voice for that language.
                val preferred = Locale.getDefault()
                val availability = engine.isLanguageAvailable(preferred)
                engine.language =
                    if (availability >= TextToSpeech.LANG_AVAILABLE) preferred else Locale.ENGLISH
            }
        }
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MILLIS) }
        runCatching {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
        }
    }

    fun release() {
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
        runCatching { tone?.release() }
        tone = null
        isReady = false
    }

    private companion object {
        const val TONE_VOLUME = 100
        const val BEEP_MILLIS = 350
    }
}

/** Ties the engine's lifetime to the screen holding it, so it is shut down on the way out. */
@Composable
fun rememberOrderAnnouncer(): OrderAnnouncer {
    val context = LocalContext.current
    val announcer = remember { OrderAnnouncer(context) }
    DisposableEffect(Unit) {
        onDispose { announcer.release() }
    }
    return announcer
}
