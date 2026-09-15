package com.duggustore.app.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/** What the voice sheet is doing right now. */
sealed interface VoiceState {
    /** Closed. */
    object Idle : VoiceState
    object Listening : VoiceState
    object Working : VoiceState
    data class Failed(val message: String) : VoiceState
    /** Permission refused; the sheet offers to ask again. */
    object NeedsPermission : VoiceState
}

/**
 * Drives speech recognition in-process.
 *
 * The mic used to fire RecognizerIntent, which hands the screen to Google's own
 * listening UI — a different typeface, a different colour, and Google's name on
 * it. Running SpeechRecognizer directly keeps the user inside this app, so the
 * sheet can carry the store's own look.
 */
@Stable
class VoiceSearchController internal constructor(
    private val context: Context,
    private val onResult: (String) -> Unit
) {
    var state by mutableStateOf<VoiceState>(VoiceState.Idle)
        private set

    /** What has been heard so far, shown live while the user is still talking. */
    var partial by mutableStateOf("")
        private set

    /** Microphone level, 0..1, drives the ring around the mic. */
    var level by mutableStateOf(0f)
        private set

    private var recognizer: SpeechRecognizer? = null

    internal var requestPermission: (() -> Unit)? = null

    val isOpen: Boolean get() = state != VoiceState.Idle

    fun open() {
        partial = ""
        level = 0f
        if (!hasMicPermission(context)) {
            state = VoiceState.NeedsPermission
            requestPermission?.invoke()
            return
        }
        startListening()
    }

    fun retryPermission() {
        requestPermission?.invoke()
    }

    internal fun onPermissionResult(granted: Boolean) {
        if (granted) startListening() else state = VoiceState.NeedsPermission
    }

    fun close() {
        release()
        state = VoiceState.Idle
        partial = ""
        level = 0f
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            state = VoiceState.Failed("Voice search isn't available on this device")
            return
        }

        release()
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        state = VoiceState.Listening
        try {
            speech.startListening(intent)
        } catch (e: SecurityException) {
            state = VoiceState.NeedsPermission
        }
    }

    internal fun release() {
        recognizer?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (e: Exception) {
                // Already gone.
            }
        }
        recognizer = null
    }

    // RecognitionListener's methods are all abstract, so every one is written out.
    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            state = VoiceState.Listening
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // The API reports roughly -2..10 dB; mapped to 0..1 for the ring.
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            state = VoiceState.Working
        }

        override fun onError(error: Int) {
            state = when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceState.NeedsPermission
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    VoiceState.Failed("Didn't catch that. Try again.")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    VoiceState.Failed("No connection for voice search")
                SpeechRecognizer.ERROR_AUDIO -> VoiceState.Failed("Couldn't use the microphone")
                else -> VoiceState.Failed("Voice search didn't work. Try again.")
            }
            level = 0f
        }

        override fun onResults(results: Bundle?) {
            val spoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (spoken.isNullOrEmpty()) {
                state = VoiceState.Failed("Didn't catch that. Try again.")
            } else {
                onResult(spoken)
                close()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let { partial = it }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

/**
 * Returns a controller, or null when the device has no recogniser at all — the
 * search bar leaves the mic out for a null handler rather than showing a button
 * that cannot work.
 */
@Composable
fun rememberVoiceSearchController(onResult: (String) -> Unit): VoiceSearchController? {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)

    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    if (!available) return null

    val controller = remember {
        VoiceSearchController(context) { currentOnResult(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> controller.onPermissionResult(granted) }

    // Asked on the mic tap rather than on launch, so nobody sees a microphone
    // prompt for a feature they have not reached for.
    controller.requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    return controller
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
