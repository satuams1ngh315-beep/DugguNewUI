package com.duggustore.app.ui.screens.delivery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.data.repository.RoutingRepository
import com.duggustore.app.platform.LocationState
import com.duggustore.app.platform.rememberDeviceLocation
import com.duggustore.app.ui.components.OsmMapView
import com.duggustore.app.ui.theme.*
import org.osmdroid.util.GeoPoint
import kotlin.math.roundToInt

/**
 * In-app turn-by-turn view for a rider's pickup or drop stop: a satellite
 * map (osmdroid, tiled from Esri's free World Imagery service) with the
 * real road route from OSRM's public routing server drawn between the
 * rider's live position and the destination — free and accurate, with no
 * external maps app hand-off and no API key.
 *
 * The map runs full-bleed behind everything else, with the header and the
 * distance/ETA readout floating over it as their own rounded cards —
 * closer to how a dedicated navigation app is laid out than a map boxed in
 * between two flat bars.
 */
@Composable
fun RouteMapScreen(
    title: String,
    destinationLabel: String,
    destinationLat: Double,
    destinationLng: Double,
    onBack: () -> Unit
) {
    val detected = rememberDeviceLocation()
    val originState = detected.state as? LocationState.Found
    // Kept across recompositions rather than rebuilt each time: a fresh
    // GeoPoint for the same coordinates would re-run the map's update pass
    // for nothing.
    val origin = remember(originState?.latitude, originState?.longitude) {
        originState?.let { GeoPoint(it.latitude, it.longitude) }
    }
    val destination = remember(destinationLat, destinationLng) { GeoPoint(destinationLat, destinationLng) }

    val routingRepo = remember { RoutingRepository() }
    var routePoints by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var routeInfo by remember { mutableStateOf<Pair<Double, Double>?>(null) } // metres, seconds
    var routeFailed by remember { mutableStateOf(false) }
    var isRouting by remember { mutableStateOf(false) }
    // Bumped by the retry tap. These are shared, rate-limited routing servers,
    // so a refused request is worth asking again for rather than leaving the
    // rider on a straight line for the rest of the trip.
    var retryToken by remember { mutableStateOf(0) }

    LaunchedEffect(originState?.latitude, originState?.longitude, retryToken) {
        val fix = originState ?: return@LaunchedEffect
        routeFailed = false
        isRouting = true
        routingRepo.getDrivingRoute(fix.latitude, fix.longitude, destinationLat, destinationLng)
            .onSuccess { result ->
                routePoints = result.points.map { GeoPoint(it.latitude, it.longitude) }
                routeInfo = result.distanceMetres to result.durationSeconds
            }
            .onFailure {
                // A straight line between the two points is still a usable
                // map even when the routing call itself couldn't be reached.
                routeFailed = true
                routePoints = null
                routeInfo = null
            }
        isRouting = false
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        OsmMapView(
            destination = destination,
            destinationLabel = destinationLabel,
            origin = origin,
            routePoints = routePoints,
            modifier = Modifier.fillMaxSize()
        )

        // Floating header — a rounded card over the map rather than a flat
        // bar spanning the top, so the map reads as the whole screen with
        // controls resting on it, not a strip carved out of it.
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceWhite,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(TealSurface),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TealDark)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = destinationLabel,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (origin == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 74.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = OrangeSurface,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = when (detected.state) {
                        LocationState.Locating -> "Finding your location…"
                        is LocationState.Unavailable -> "Turn on location to see your position and the route"
                        else -> "Waiting for your location…"
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    fontSize = 12.sp,
                    color = OrangeDark
                )
            }
        }

        // Floating distance/ETA pill, bottom-anchored the same way the
        // header floats at the top.
        val (distance, duration) = routeInfo ?: (null to null)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceWhite,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Coral, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = when {
                                distance != null -> "%.1f km".format(distance / 1000)
                                isRouting -> "Finding route…"
                                routeFailed -> "Showing direct line"
                                else -> "Locating…"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        // Without this the straight line looks like the route
                        // itself, with nothing saying the road route never
                        // arrived or that asking again might get it.
                        if (routeFailed && !isRouting) {
                            Text(
                                text = "Couldn't reach the routing service · Retry",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Teal,
                                modifier = Modifier.clickable { retryToken++ }
                            )
                        }
                    }
                }
                if (duration != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, null, tint = Teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${(duration / 60).roundToInt()} min",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
