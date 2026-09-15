package com.duggustore.app.platform

import android.location.Geocoder
import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.duggustore.app.data.model.DeliveryTracking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** What the tracking card needs beyond the raw coordinates. */
@Stable
data class RiderPosition(
    val distanceMetres: Float? = null,
    val ageMinutes: Long? = null
)

/**
 * Turns the rider's raw fix into "how far" and "how fresh".
 *
 * The delivery address is stored as text, so the distance depends on geocoding
 * it. That can fail — no geocoder backend, no network, an address the service
 * cannot place — and when it does the card simply drops the distance line
 * rather than showing a wrong number.
 */
@Composable
fun rememberRiderPosition(
    tracking: DeliveryTracking?,
    deliveryAddress: String
): RiderPosition {
    val context = LocalContext.current
    var destination by remember(deliveryAddress) { mutableStateOf<Location?>(null) }

    LaunchedEffect(deliveryAddress) {
        destination = geocode(context, deliveryAddress)
    }

    return remember(tracking, destination) {
        if (tracking == null || !tracking.hasFix()) return@remember RiderPosition()

        val distance = destination?.let { target ->
            val results = FloatArray(1)
            Location.distanceBetween(
                tracking.latitude,
                tracking.longitude,
                target.latitude,
                target.longitude,
                results
            )
            results[0]
        }

        RiderPosition(
            distanceMetres = distance,
            ageMinutes = minutesSince(tracking.updatedAt)
        )
    }
}

/**
 * The synchronous Geocoder.getFromLocationName() is deprecated in favour of
 * a listener-based overload added in API 33, but that overload exists only
 * to avoid blocking the caller — already handled here by running on
 * Dispatchers.IO, so there's nothing left for the async version to fix.
 */
@Suppress("DEPRECATION")
private suspend fun geocode(
    context: android.content.Context,
    address: String
): Location? = withContext(Dispatchers.IO) {
    if (address.isBlank() || !Geocoder.isPresent()) return@withContext null
    try {
        Geocoder(context).getFromLocationName(address, 1)?.firstOrNull()?.let { entry ->
            Location("address").apply {
                latitude = entry.latitude
                longitude = entry.longitude
            }
        }
    } catch (e: Exception) {
        // Throws on a dead network and on devices with no geocoder backend.
        null
    }
}

/**
 * The timestamp comes back from PostgREST as ISO 8601, but with a varying
 * fractional-second part and either a Z or an offset, so it is tried against a
 * few shapes rather than one.
 */
private fun minutesSince(timestamp: String): Long? {
    if (timestamp.isBlank()) return null

    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss"
    )

    for (pattern in patterns) {
        try {
            val format = SimpleDateFormat(pattern, Locale.US)
            // The trailing shapes carry no zone, and PostgREST stores UTC.
            if (!pattern.endsWith("XXX")) {
                format.timeZone = TimeZone.getTimeZone("UTC")
            }
            // SimpleDateFormat ignores trailing characters it has no pattern
            // for, so a "Z" on the zoneless shapes is harmless given the zone
            // is forced to UTC above.
            val parsed = format.parse(timestamp) ?: continue
            val millis = Date().time - parsed.time
            return (millis / 60_000L).coerceAtLeast(0L)
        } catch (e: Exception) {
            // Try the next shape.
        }
    }
    return null
}
