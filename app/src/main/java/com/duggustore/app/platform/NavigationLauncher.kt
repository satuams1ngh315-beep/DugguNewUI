package com.duggustore.app.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens turn-by-turn directions in whatever navigation app the device has —
 * Google Maps if it's installed, otherwise any app that understands a plain
 * geo: query. Drawing routes in-app would need a paid Directions/Maps SDK
 * and an API key this project doesn't have; every delivery app hands this
 * off to the device's own maps app instead, which already knows live
 * traffic and road conditions.
 *
 * [latitude]/[longitude] null (or both 0.0, the column default for "never
 * set") falls back to a text search on [addressText] — a store that hasn't
 * set its location yet, or an order placed against a hand-typed address,
 * still gets a usable button rather than a dead one.
 */
fun openNavigation(context: Context, addressText: String, latitude: Double?, longitude: Double?) {
    val hasFix = latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)

    val mapsUri = if (hasFix) {
        Uri.parse("google.navigation:q=$latitude,$longitude&mode=d")
    } else {
        Uri.parse("geo:0,0?q=${Uri.encode(addressText.ifBlank { "destination" })}")
    }

    val mapsIntent = Intent(Intent.ACTION_VIEW, mapsUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(mapsIntent)
        return
    } catch (e: ActivityNotFoundException) {
        // Google Maps isn't installed — fall through to a generic geo intent
        // any map app can pick up.
    }

    val genericUri = if (hasFix) {
        Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(addressText.ifBlank { "Destination" })})")
    } else {
        mapsUri
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, genericUri))
    } catch (e: ActivityNotFoundException) {
        // No navigation app at all on this device — nothing more to do.
    }
}

/**
 * Opens the dialer with the number already typed in — ACTION_DIAL rather
 * than ACTION_CALL, so this needs no CALL_PHONE permission and the person
 * placing the call still has to tap the call button themselves.
 */
fun openDialer(context: Context, phone: String) {
    if (phone.isBlank()) return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    } catch (e: ActivityNotFoundException) {
        // No dialer app on this device — nothing more to do.
    }
}
