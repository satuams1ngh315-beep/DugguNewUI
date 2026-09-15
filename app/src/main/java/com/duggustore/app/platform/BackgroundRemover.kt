package com.duggustore.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Cuts a product photo's subject out from its background, on-device via
 * ML Kit's subject segmentation — free, no API key, no server round trip.
 * Returns null on any failure (a photo the model can't segment, the model
 * still downloading, no Play Services) rather than throwing, so a seller's
 * upload never blocks on it — the caller falls back to the original photo.
 */
object BackgroundRemover {

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()

    suspend fun removeBackground(bitmap: Bitmap): Bitmap? {
        val segmenter = SubjectSegmentation.getClient(options)
        return try {
            suspendCancellableCoroutine { continuation ->
                segmenter.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.foregroundBitmap)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                continuation.invokeOnCancellation { segmenter.close() }
            }
        } catch (e: Exception) {
            null
        } finally {
            segmenter.close()
        }
    }

    /**
     * Decodes to no larger than [maxDimension] on the longer side — a
     * seller's photo can come straight off a 12MP+ camera, and segmenting
     * (and holding several of them across a multi-photo upload) at full
     * resolution risks an OutOfMemoryError for no visible benefit at the
     * card/detail sizes these are ever shown at.
     */
    fun decodeScaledBitmap(bytes: ByteArray, maxDimension: Int = 1600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
