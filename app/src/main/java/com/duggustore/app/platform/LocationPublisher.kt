package com.duggustore.app.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** How often the rider's position is written while sharing is on. */
private const val UPDATE_INTERVAL_MS = 15_000L

/** And how far they have to move before an update is worth writing. */
private const val UPDATE_DISTANCE_M = 25f

/**
 * Streams the rider's position while [enabled] and hands each fix to [onFix].
 *
 * This only runs while the app is in the foreground: the listener is registered
 * by a composable and removed when it leaves the composition. Tracking a rider
 * whose phone is in their pocket needs a foreground service with its own
 * notification and the FOREGROUND_SERVICE_LOCATION permission, which is a
 * larger change than this.
 */
@Composable
fun RiderLocationPublisher(
    enabled: Boolean,
    onFix: (Location) -> Unit,
    onPermissionDenied: () -> Unit = {},
    onUnavailable: () -> Unit = {}
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasFineLocation(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.any { it }
        if (!granted) onPermissionDenied()
    }

    // Asking only once sharing is switched on keeps the system dialog off the
    // screen for a rider who has not opted in.
    LaunchedEffect(enabled) {
        if (enabled && !granted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val currentOnFix by rememberUpdatedState(onFix)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)

    DisposableEffect(enabled, granted) {
        if (!enabled || !granted) return@DisposableEffect onDispose { }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            currentOnUnavailable()
            return@DisposableEffect onDispose { }
        }

        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            currentOnUnavailable()
            return@DisposableEffect onDispose { }
        }

        // Written out rather than as a lambda: onStatusChanged and the two
        // provider callbacks only became default methods in API 30, so a SAM
        // lambda raises AbstractMethodError on older devices.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = currentOnFix(location)
            // Still required for the same pre-API-30 ABI reason as above,
            // even though the platform interface deprecated it.
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        try {
            @Suppress("MissingPermission")
            manager.requestLocationUpdates(
                provider,
                UPDATE_INTERVAL_MS,
                UPDATE_DISTANCE_M,
                listener,
                Looper.getMainLooper()
            )
            // A stationary rider produces no updates, so seed with whatever the
            // system already has rather than showing the customer nothing.
            @Suppress("MissingPermission")
            manager.getLastKnownLocation(provider)?.let { currentOnFix(it) }
        } catch (e: SecurityException) {
            currentOnUnavailable()
        }

        onDispose {
            try {
                manager.removeUpdates(listener)
            } catch (e: SecurityException) {
                // Nothing left to remove.
            }
        }
    }
}

private fun hasFineLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
