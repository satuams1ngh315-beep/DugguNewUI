package com.duggustore.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.duggustore.app.R
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Esri World Imagery is reliably populated up to about here worldwide; past it, rural areas in particular fall back to blank grey tiles. */
private const val MAX_USEFUL_ZOOM = 17.0

/**
 * The map and the overlays drawn on it, built once and then kept. Holding the
 * overlays rather than recreating them is what lets a new location fix move the
 * existing marker instead of every overlay being torn down and added again.
 */
private class RouteMapHolder(
    val view: MapView,
    val haloLine: Polyline,
    val routeLine: Polyline,
    val originMarker: Marker,
    val destinationMarker: Marker
)

private fun buildRouteMap(context: Context, routeColor: Int): RouteMapHolder {
    val view = MapView(context).apply {
        setTileSource(EsriSatelliteTileSource)
        setMultiTouchControls(true)
        controller.setZoom(16.0)
    }

    // A wider white line underneath the coloured one, so the route stays
    // legible over light-coloured roofs and roads in the satellite imagery
    // instead of blending into them.
    val haloLine = Polyline(view).apply {
        outlinePaint.color = android.graphics.Color.WHITE
        outlinePaint.strokeWidth = 14f
    }
    val routeLine = Polyline(view).apply {
        outlinePaint.color = routeColor
        outlinePaint.strokeWidth = 8f
    }
    val originMarker = Marker(view).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.marker_you)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = "You"
        infoWindow = null
    }
    val destinationMarker = Marker(view).apply {
        icon = ContextCompat.getDrawable(context, R.drawable.marker_pin)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        // The bottom info card already carries the label; the default osmdroid
        // tooltip bubble is plain and unbranded, so it's left off rather than
        // tapping into it.
        infoWindow = null
    }

    // Lines first, so both markers draw on top of the route rather than under it.
    view.overlays.add(haloLine)
    view.overlays.add(routeLine)
    view.overlays.add(originMarker)
    view.overlays.add(destinationMarker)

    return RouteMapHolder(view, haloLine, routeLine, originMarker, destinationMarker)
}

/**
 * Satellite map via osmdroid, tiled from Esri's free World Imagery service
 * rather than a plain street map — no API key or billing account either
 * way, unlike the Google Maps SDK. Draws an origin marker (the rider, when
 * a fix is available) as a plain dot, a destination pin, and either the
 * real road route (when [routePoints] came back from OSRM) or a straight
 * line between the two as a fallback.
 *
 * The camera is framed once — on the destination, then again when the first
 * position fix arrives — and left alone after that. Re-framing on every fix
 * made the map jump on each GPS update and undid whatever the rider had
 * panned or zoomed to.
 */
@Composable
fun OsmMapView(
    destination: GeoPoint,
    destinationLabel: String,
    modifier: Modifier = Modifier,
    origin: GeoPoint? = null,
    routePoints: List<GeoPoint>? = null,
    routeColor: Int = android.graphics.Color.parseColor("#2BB3AC")
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val holder = remember { buildRouteMap(context, routeColor) }
    val mapView = holder.view

    // Keyed on the destination so opening a different stop frames itself
    // afresh, while updates within one stop leave the camera where it is.
    var centredOnDestination by remember(destination) { mutableStateOf(false) }
    var framedWithOrigin by remember(destination) { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            holder.destinationMarker.position = destination
            holder.destinationMarker.title = destinationLabel

            // Nothing to draw between two points until there is a fix to draw
            // from, so the route and the rider's own dot stay hidden until then.
            val hasOrigin = origin != null
            holder.haloLine.isEnabled = hasOrigin
            holder.routeLine.isEnabled = hasOrigin
            holder.originMarker.isEnabled = hasOrigin

            if (origin != null) {
                val linePoints = routePoints?.takeIf { it.size >= 2 } ?: listOf(origin, destination)
                holder.haloLine.setPoints(linePoints)
                holder.routeLine.setPoints(linePoints)
                holder.routeLine.outlinePaint.color = routeColor
                holder.originMarker.position = origin
            }

            when {
                origin != null && !framedWithOrigin -> {
                    framedWithOrigin = true
                    // Posted rather than called directly: the view needs a
                    // measured size first, which it doesn't have on the same
                    // pass it was created.
                    view.post {
                        // Unanimated: zoomLevelDouble below needs to already
                        // reflect the box-fit zoom, not a value mid-animation.
                        view.zoomToBoundingBox(
                            BoundingBox.fromGeoPoints(listOf(origin, destination)),
                            false,
                            140
                        )
                        // When origin and destination are (near) the same point —
                        // a rider standing at the pickup spot — the box has ~zero
                        // area, so fitting it zooms all the way to the tile
                        // source's max (19). Esri's satellite imagery has no real
                        // photography that close in most areas, especially rural
                        // ones, and serves flat grey placeholder tiles instead —
                        // capping the zoom keeps it on a level that actually has
                        // imagery.
                        if (view.zoomLevelDouble > MAX_USEFUL_ZOOM) {
                            view.controller.setZoom(MAX_USEFUL_ZOOM)
                        }
                    }
                }
                origin == null && !centredOnDestination -> {
                    centredOnDestination = true
                    view.post { view.controller.setCenter(destination) }
                }
            }

            view.invalidate()
        }
    )
}
