package com.duggustore.app.ui.screens.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.data.model.Address
import com.duggustore.app.ui.components.AuthField
import com.duggustore.app.ui.components.DashboardEmpty
import com.duggustore.app.ui.components.LocationPickerField
import com.duggustore.app.ui.components.ListSkeleton
import com.duggustore.app.ui.components.ScreenHeader
import com.duggustore.app.ui.components.dugguClickable
import com.duggustore.app.ui.theme.*

@Composable
fun AddressesScreen(
    addresses: List<Address>,
    isLoading: Boolean,
    onSaveAddress: (label: String, fullAddress: String, isDefault: Boolean, existingId: String, latitude: Double, longitude: Double) -> Unit,
    onDeleteAddress: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onBack: () -> Unit,
    /** When set, picking an address returns it to the caller instead of just managing the list. */
    onSelectAddress: ((Address) -> Unit)? = null
) {
    var editing by remember { mutableStateOf<Address?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        ScreenHeader(
            title = if (onSelectAddress != null) stringResource(R.string.addresses_choose)
                    else stringResource(R.string.addresses_title),
            subtitle = if (addresses.size == 1) stringResource(R.string.addresses_one_saved)
                       else stringResource(R.string.addresses_count_saved, addresses.size),
            onBack = onBack
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && addresses.isEmpty() -> ListSkeleton()

                addresses.isEmpty() -> DashboardEmpty(
                    icon = Icons.Default.LocationOff,
                    title = stringResource(R.string.addresses_empty_title),
                    subtitle = stringResource(R.string.addresses_empty_subtitle)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(addresses, key = { it.id }) { address ->
                        AddressCard(
                            address = address,
                            onClick = { onSelectAddress?.invoke(address) ?: onSetDefault(address.id) },
                            onEdit = { editing = address; showSheet = true },
                            onDelete = { onDeleteAddress(address.id) }
                        )
                    }
                }
            }
        }

        Surface(
            color = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            shadowElevation = 18.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { editing = null; showSheet = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = Dimens.ShapeMd,
                    colors = ButtonDefaults.buttonColors(containerColor = BlinkitGreen)
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.addresses_add_new),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    if (showSheet) {
        AddressDialog(
            existing = editing,
            onDismiss = { showSheet = false; editing = null },
            onSave = { label, full, isDefault, lat, lng ->
                onSaveAddress(label, full, isDefault, editing?.id ?: "", lat, lng)
                showSheet = false
                editing = null
            }
        )
    }
}

@Composable
private fun AddressCard(
    address: Address,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().dugguClickable { onClick() },
        shape = Dimens.ShapeMd,
        color = if (address.isDefault) BlinkitGreenSurface else SurfaceWhite,
        shadowElevation = if (address.isDefault) 0.dp else 2.dp,
        border = if (address.isDefault) BorderStroke(1.5.dp, BlinkitGreen) else null
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (address.isDefault) Icons.Default.CheckCircle
                              else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (address.isDefault) stringResource(R.string.addresses_default)
                                    else stringResource(R.string.addresses_set_default),
                tint = if (address.isDefault) BlinkitGreen else TextLight,
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = address.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (address.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = Dimens.ShapeSm, color = Teal) {
                            Text(
                                text = stringResource(R.string.addresses_default_badge),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = address.fullAddress,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit,
                    stringResource(R.string.addresses_edit_label, address.label),
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(R.string.addresses_delete_label, address.label),
                    tint = Coral,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddressDialog(
    existing: Address?,
    onDismiss: () -> Unit,
    onSave: (label: String, fullAddress: String, isDefault: Boolean, latitude: Double, longitude: Double) -> Unit
) {
    val defaultLabel = stringResource(R.string.addresses_default_home)
    var label by remember { mutableStateOf(existing?.label ?: defaultLabel) }
    var fullAddress by remember { mutableStateOf(existing?.fullAddress ?: "") }
    var isDefault by remember { mutableStateOf(existing?.isDefault ?: false) }
    var latitude by remember { mutableStateOf(existing?.latitude ?: 0.0) }
    var longitude by remember { mutableStateOf(existing?.longitude ?: 0.0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        shape = Dimens.ShapeLg,
        title = {
            Text(
                text = if (existing == null) stringResource(R.string.addresses_add_title)
                       else stringResource(R.string.addresses_edit_title),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                AuthField(
                    value = label,
                    onValueChange = { label = it },
                    label = stringResource(R.string.addresses_field_label),
                    placeholder = stringResource(R.string.addresses_label_hint)
                )
                Spacer(Modifier.height(14.dp))
                // LocationPickerField handles both typed entry and the GPS/map picker.
                // When the user picks via GPS or map the address text AND coordinates
                // are updated together; if they type manually, coordinates stay at 0.0
                // which is handled gracefully downstream (rider just won't have nav).
                LocationPickerField(
                    address = fullAddress,
                    onAddressChange = { fullAddress = it },
                    onLocationPicked = { addr, lat, lng ->
                        fullAddress = addr
                        latitude = lat
                        longitude = lng
                    },
                    label = stringResource(R.string.addresses_full_address)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.dugguClickable { isDefault = !isDefault },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it },
                        colors = CheckboxDefaults.colors(checkedColor = BlinkitGreen)
                    )
                    Text(stringResource(R.string.addresses_set_default), fontSize = 14.sp, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label, fullAddress, isDefault, latitude, longitude) },
                enabled = fullAddress.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save), color = Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextSecondary) }
        }
    )
}
