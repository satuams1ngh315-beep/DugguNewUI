package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.duggustore.app.platform.LocationState
import com.duggustore.app.platform.reverseGeocodeAddress
import com.duggustore.app.platform.rememberDeviceLocation
import com.duggustore.app.ui.theme.*
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * An address field with two real ways to pin it down exactly, not just type
 * it: a device GPS fix, or dragging a satellite map under a fixed centre pin
 * — the same picker pattern delivery apps use for a store or drop location.
 * Both feed back a lat/lng alongside the address text.
 */
@Composable
fun LocationPickerField(
    address: String,
    onAddressChange: (String) -> Unit,
    onLocationPicked: (address: String, lat: Double, lng: Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var showMapPicker by remember { mutableStateOf(false) }
    val detected = rememberDeviceLocation(autoStart = false)
    val found = detected.state as? LocationState.Found
    val busy = detected.state is LocationState.Locating

    LaunchedEffect(found) {
        val fix = found ?: return@LaunchedEffect
        onAddressChange(fix.address)
        onLocationPicked(fix.address, fix.latitude, fix.longitude)
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text(label, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceWhite,
                unfocusedContainerColor = SurfaceWhite,
                focusedBorderColor = Teal,
                unfocusedBorderColor = BorderGray
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { detected.refresh() },
                enabled = !busy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Teal)
                } else {
                    Icon(Icons.Default.MyLocation, null, tint = Teal, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Current location", color = Teal, fontSize = 12.sp)
                }
            }
            OutlinedButton(
                onClick = { showMapPicker = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Map, null, tint = Teal, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pick on map", color = Teal, fontSize = 12.sp)
            }
        }
    }

    if (showMapPicker) {
        MapPickerDialog(
            initialLat = found?.latitude,
            initialLng = found?.longitude,
            onDismiss = { showMapPicker = false },
            onConfirm = { pickedAddress, lat, lng ->
                showMapPicker = false
                onAddressChange(pickedAddress)
                onLocationPicked(pickedAddress, lat, lng)
            }
        )
    }
}

/** Roughly the centre of India — used only when no other starting point (a GPS fix, an existing saved point) is available. */
private val DEFAULT_MAP_CENTER = GeoPoint(22.9734, 78.6569)

@Composable
private fun MapPickerDialog(
    initialLat: Double?,
    initialLng: Double?,
    onDismiss: () -> Unit,
    onConfirm: (address: String, lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var center by remember {
        mutableStateOf(
            if (initialLat != null && initialLng != null) GeoPoint(initialLat, initialLng) else DEFAULT_MAP_CENTER
        )
    }
    var isResolving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = Teal) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            // A Dialog is its own window, and how much (if any)
                            // status-bar inset it reports back can differ from
                            // the main activity's — leaving the bar's height to
                            // padding alone let it come out too short on some
                            // devices, clipping the title against the map below
                            // instead of leaving room for it.
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, "Close", tint = Color.White)
                        }
                        Text(
                            "Drag the map to place the pin",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    CenteredMap(
                        initialCenter = center,
                        onCenterChanged = { center = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        Icons.Default.Map,
                        contentDescription = "Selected point",
                        tint = Coral,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                    )
                }

                Surface(color = SurfaceWhite, shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "%.5f, %.5f".format(center.latitude, center.longitude),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isResolving = true
                                    val resolved = reverseGeocodeAddress(context, center.latitude, center.longitude)
                                    val label = resolved?.takeIf { it.isNotBlank() }
                                        ?: "%.5f, %.5f".format(center.latitude, center.longitude)
                                    isResolving = false
                                    onConfirm(label, center.latitude, center.longitude)
                                }
                            },
                            enabled = !isResolving,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal)
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Confirm this location", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Plain pannable satellite map with no built-in marker — the fixed pin sits in Compose on top of it, in [MapPickerDialog]. */
@Composable
private fun CenteredMap(
    initialCenter: GeoPoint,
    onCenterChanged: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(EsriSatelliteTileSource)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            controller.setCenter(initialCenter)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                onCenterChanged(GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude))
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                onCenterChanged(GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude))
                return true
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        mapView.addMapListener(mapListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            mapView.removeMapListener(mapListener)
            mapView.onDetach()
        }
    }

    AndroidView(modifier = modifier, factory = { mapView })
}
