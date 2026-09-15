package com.duggustore.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.duggustore.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** What the location strip should be showing. */
sealed interface LocationState {
    /** Nothing asked for yet, or permission was never granted. */
    object Idle : LocationState
    object Locating : LocationState
    data class Found(val address: String, val latitude: Double, val longitude: Double) : LocationState
    /** Permission refused, location switched off, or nothing came back. */
    data class Unavailable(@StringRes val messageRes: Int) : LocationState
}

/** Holds the detected location and the action that (re)fetches it. */
@Stable
class DeviceLocation internal constructor(
    val state: LocationState,
    val refresh: () -> Unit
)

/**
 * Reads the device's location and turns it into a readable address.
 *
 * Uses the framework LocationManager rather than Play Services, so it needs no
 * extra dependency. It asks for the permission the first time and afterwards
 * refreshes on demand.
 */
@Composable
fun rememberDeviceLocation(autoStart: Boolean = true): DeviceLocation {
    val context = LocalContext.current
    var state by remember { mutableStateOf<LocationState>(LocationState.Idle) }
    var requestToken by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            requestToken++
        } else {
            state = LocationState.Unavailable(R.string.location_permission_denied)
        }
    }

    fun start() {
        if (hasLocationPermission(context)) {
            requestToken++
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    // The first pass only starts anything when autoStart is on and the permission
    // is already held, so opening the app does not throw a system dialog at
    // someone before they have seen the screen.
    LaunchedEffect(Unit) {
        if (autoStart && hasLocationPermission(context)) requestToken++
    }

    LaunchedEffect(requestToken) {
        if (requestToken == 0) return@LaunchedEffect
        state = LocationState.Locating
        state = resolveLocation(context)
    }

    return remember(state) { DeviceLocation(state = state, refresh = ::start) }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private suspend fun resolveLocation(context: Context): LocationState {
    val location = currentLocation(context)
        ?: return LocationState.Unavailable(R.string.location_turn_on)
    val address = reverseGeocodeAddress(context, location.latitude, location.longitude)
    val label = if (address.isNullOrBlank()) {
        // Still a real fix, just no street name for it.
        "%.4f, %.4f".format(location.latitude, location.longitude)
    } else {
        address
    }
    return LocationState.Found(label, location.latitude, location.longitude)
}

private suspend fun currentLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    // A recent fix from any provider is good enough for "which area am I in".
    val cached = try {
        manager.allProviders
            .mapNotNull { @Suppress("MissingPermission") manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    } catch (e: SecurityException) {
        null
    }
    if (cached != null) return cached

    // Nothing cached: ask for one fresh fix. Only API 30 and up can do that
    // without registering a repeating listener.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val provider = when {
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> return null
    }

    return suspendCoroutine { continuation ->
        try {
            @Suppress("MissingPermission")
            manager.getCurrentLocation(
                provider,
                null,
                context.mainExecutor
            ) { location -> continuation.resume(location) }
        } catch (e: SecurityException) {
            continuation.resume(null)
        }
    }
}

/**
 * Geocoding blocks, so it never runs on the main thread — which is also why
 * the synchronous Geocoder.getFromLocation() is fine to keep here rather
 * than migrating to the API 33+ listener-based overload: the thing that
 * overload exists to avoid (blocking the caller) is already handled by
 * running this on Dispatchers.IO.
 *
 * Public so the map location picker can reverse-geocode an arbitrary point
 * the user panned to, not just a device GPS fix.
 */
@Suppress("DEPRECATION")
suspend fun reverseGeocodeAddress(context: Context, latitude: Double, longitude: Double): String? =
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            val entry = Geocoder(context)
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?: return@withContext null

            // Street and area read better than the full one-line address, which
            // repeats the country on every row.
            listOfNotNull(
                entry.subLocality ?: entry.thoroughfare,
                entry.locality ?: entry.subAdminArea,
                entry.postalCode
            ).distinct().joinToString(", ").ifBlank { entry.getAddressLine(0) }
        } catch (e: Exception) {
            // Geocoder throws on no backend service and on a dead network.
            null
        }
    }
