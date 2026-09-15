package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.data.model.Address
import com.duggustore.app.platform.LocationState
import com.duggustore.app.ui.theme.*

/**
 * Delivery-location picker that rises from the bottom of the screen.
 *
 * Tapping the location strip used to push the whole addresses screen, which took
 * the customer off home to change one line. This keeps them where they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSheet(
    visible: Boolean,
    locationState: LocationState,
    addresses: List<Address>,
    onDetectLocation: () -> Unit,
    onUseDetected: (String, Double, Double) -> Unit,
    onSelectAddress: (Address) -> Unit,
    onAddNewAddress: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.location_sheet_title),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.location_sheet_subtitle),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))

            DetectedLocationRow(
                state = locationState,
                onDetect = onDetectLocation,
                onUse = onUseDetected,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(18.dp))

            if (addresses.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.location_saved_addresses).uppercase(),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextLight
                )
                Spacer(Modifier.height(8.dp))

                // Capped so a long list scrolls inside the sheet rather than
                // pushing "Add a new address" off the bottom.
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(addresses, key = { it.id }) { address ->
                        SavedAddressRow(
                            address = address,
                            onClick = { onSelectAddress(address) }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrangeSurface)
                    .clickable { onAddNewAddress() }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = OrangeDark,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.location_add_new),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrangeDark
                )
            }
        }
    }
}

@Composable
private fun DetectedLocationRow(
    state: LocationState,
    onDetect: () -> Unit,
    onUse: (String, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val found = state as? LocationState.Found
    val busy = state is LocationState.Locating

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TealSurface)
            .clickable(enabled = !busy) {
                if (found != null) onUse(found.address, found.latitude, found.longitude) else onDetect()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Teal),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (found != null) stringResource(R.string.location_use_current)
                       else stringResource(R.string.location_detect),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TealDark
            )
            Text(
                text = when (state) {
                    is LocationState.Found -> state.address
                    LocationState.Locating -> stringResource(R.string.location_finding)
                    is LocationState.Unavailable -> stringResource(R.string.location_tap_retry)
                    LocationState.Idle -> stringResource(R.string.location_using_gps)
                },
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 2,
                lineHeight = 17.sp
            )
        }

        if (found != null) {
            // 40dp target, not the bare 20dp icon: a tap target under ~40dp
            // is hard to hit reliably, and this one sits in a moving hand
            // next to the row's own tap area.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .dugguClickable { onDetect() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.location_detect_again),
                    tint = Teal,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedAddressRow(address: Address, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (address.isDefault) TealSurface else SurfaceMuted)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (address.isDefault) Icons.Default.CheckCircle
                          else Icons.Default.LocationOn,
            contentDescription = null,
            tint = if (address.isDefault) Teal else TextLight,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = address.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = address.fullAddress,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
