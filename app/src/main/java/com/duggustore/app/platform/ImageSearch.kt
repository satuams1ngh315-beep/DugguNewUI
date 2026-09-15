package com.duggustore.app.platform

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Search by photo: names what is in a picture on-device, and that word goes
 * into the ordinary text search.
 *
 * ML Kit's labeller is a general classifier, so what comes back is the kind
 * of thing in the shot — "Bottle", "Snack", "Fruit" — not a brand or a
 * specific product. That is the ceiling for matching a photo without a
 * server holding an image index, and it is enough to land the shopper in the
 * right part of the catalogue rather than at an empty search.
 */
object ImageSearch {

    // Below this the labeller is guessing more than recognising, and a wrong
    // word is worse than telling the shopper to retake the photo.
    private const val MIN_CONFIDENCE = 0.6f

    /** The most confident label for [image], or null if nothing was clear enough. */
    suspend fun labelFor(image: InputImage): String? {
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_CONFIDENCE)
                .build()
        )
        return try {
            suspendCancellableCoroutine { continuation ->
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        if (!continuation.isActive) return@addOnSuccessListener
                        continuation.resume(
                            labels.maxByOrNull { it.confidence }
                                ?.text
                                ?.takeIf { it.isNotBlank() }
                        )
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                continuation.invokeOnCancellation { labeler.close() }
            }
        } catch (e: Exception) {
            null
        } finally {
            labeler.close()
        }
    }

    /**
     * Labels a still straight off the camera. [rotationDegrees] comes from the
     * capture rather than being baked into the bitmap, which is what ML Kit
     * expects and saves rotating the pixels.
     */
    suspend fun labelForPhoto(jpeg: ByteArray, rotationDegrees: Int): String? {
        // Decoding is the one genuinely blocking step here — ML Kit's own
        // work is already off-thread — and this is called from a composable's
        // coroutine, which runs on the main dispatcher.
        val bitmap = withContext(Dispatchers.IO) {
            // Same downscale the seller's photo upload uses: a full-resolution
            // camera still is far more than the labeller needs, and holding
            // one risks an OutOfMemoryError for no better answer.
            BackgroundRemover.decodeScaledBitmap(jpeg)
        } ?: return null
        return labelFor(InputImage.fromBitmap(bitmap, rotationDegrees))
    }

    /** Labels a photo the shopper picked out of their gallery. */
    suspend fun labelForUri(context: Context, uri: Uri): String? {
        val image = withContext(Dispatchers.IO) {
            try {
                InputImage.fromFilePath(context, uri)
            } catch (e: Exception) {
                null
            }
        } ?: return null
        return labelFor(image)
    }
}
