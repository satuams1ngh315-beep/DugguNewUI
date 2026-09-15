package com.duggustore.app.ui.screens.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.data.model.Address
import com.duggustore.app.data.model.CartItem
import com.duggustore.app.ui.components.DeliveryEtaBanner
import com.duggustore.app.ui.components.trimAmount
import com.duggustore.app.ui.components.ScreenHeader
import com.duggustore.app.ui.components.dugguClickable
import com.duggustore.app.ui.theme.*

@Composable
fun CheckoutScreen(
    cartItems: List<CartItem>,
    addresses: List<Address>,
    subtotal: Double,
    deliveryFee: Double,
    total: Double,
    savings: Double,
    belowMinimumOrder: Boolean,
    minOrderValue: Double,
    isLoading: Boolean,
    error: String? = null,
    walletBalance: Int = 0,
    onManageAddresses: () -> Unit,
    onPlaceOrder: (deliveryAddress: String, latitude: Double?, longitude: Double?, walletAmount: Int) -> Unit,
    // Online (UPI/card) payment — only wired when Razorpay is configured; the
    // screen stays pure COD otherwise.
    onlinePayAvailable: Boolean = false,
    onPayOnline: (deliveryAddress: String, latitude: Double?, longitude: Double?, walletAmount: Int) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit
) {
    // Preselect the default address so the common case is a single tap.
    var selectedId by remember(addresses) {
        mutableStateOf(
            addresses.firstOrNull { it.isDefault }?.id ?: addresses.firstOrNull()?.id ?: ""
        )
    }
    val selected = addresses.firstOrNull { it.id == selectedId }

    var payOnline by remember { mutableStateOf(false) }
    var useWallet by remember { mutableStateOf(false) }
    val maxWalletUsable = minOf(walletBalance, total.toInt())
    val walletUsed = if (useWallet) maxWalletUsable else 0
    val payableTotal = total - walletUsed

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        CheckoutHeader(onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            DeliveryEtaBanner()
            Spacer(Modifier.height(16.dp))

            SectionTitle(Icons.Default.LocationOn, stringResource(R.string.checkout_delivery_address), Teal)
            Spacer(Modifier.height(10.dp))

            if (addresses.isEmpty()) {
                Panel(modifier = Modifier.dugguClickable { onManageAddresses() }) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(Icons.Default.AddLocationAlt, Teal)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.checkout_add_address),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                stringResource(R.string.checkout_address_required),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                addresses.forEach { address ->
                    AddressOption(
                        address = address,
                        selected = address.id == selectedId,
                        onSelect = { selectedId = address.id }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = stringResource(R.string.checkout_manage_addresses),
                    modifier = Modifier.dugguClickable { onManageAddresses() },
                    color = Teal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(22.dp))

            SectionTitle(Icons.Default.Payments, stringResource(R.string.checkout_payment), Orange)
            Spacer(Modifier.height(10.dp))

            PaymentOption(
                icon = Icons.Default.Payments,
                tint = Orange,
                title = stringResource(R.string.checkout_cod),
                subtitle = stringResource(R.string.checkout_cod_sub),
                selected = !payOnline || !onlinePayAvailable,
                selectable = onlinePayAvailable,
                onSelect = { payOnline = false }
            )

            if (onlinePayAvailable) {
                Spacer(Modifier.height(10.dp))
                PaymentOption(
                    icon = Icons.Default.CreditCard,
                    tint = Teal,
                    title = stringResource(R.string.checkout_online),
                    subtitle = stringResource(R.string.checkout_online_sub),
                    selected = payOnline,
                    selectable = true,
                    onSelect = { payOnline = true }
                )
            }

            if (walletBalance > 0) {
                Spacer(Modifier.height(10.dp))
                Panel(modifier = Modifier.dugguClickable { useWallet = !useWallet }) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(Icons.Default.AccountBalanceWallet, Teal)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.checkout_use_wallet),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                stringResource(R.string.checkout_wallet_available, walletBalance),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = useWallet,
                            onCheckedChange = { useWallet = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Teal, checkedTrackColor = TealSurface)
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            SectionTitle(Icons.Default.Receipt, stringResource(R.string.checkout_order_summary), Coral)
            Spacer(Modifier.height(10.dp))

            Panel {
                Column(modifier = Modifier.padding(16.dp)) {
                    cartItems.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.checkout_line_item,
                                    item.quantity,
                                    item.product?.name ?: stringResource(R.string.orders_item_fallback)
                                ),
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "₹${trimAmount((item.product?.effectivePrice() ?: 0.0) * item.quantity)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }

                    Divider(Modifier.padding(vertical = 10.dp), color = BorderGray)

                    SummaryRow(stringResource(R.string.cart_subtotal), "₹${trimAmount(subtotal)}")
                    SummaryRow(
                        label = stringResource(R.string.cart_delivery_fee),
                        value = if (deliveryFee <= 0.0) stringResource(R.string.cart_free) else "₹${trimAmount(deliveryFee)}",
                        valueColor = if (deliveryFee <= 0.0) SuccessGreen else TextPrimary
                    )
                    if (savings > 0) {
                        SummaryRow(stringResource(R.string.cart_you_save), "-₹${trimAmount(savings)}", SuccessGreen)
                    }
                    if (walletUsed > 0) {
                        SummaryRow(stringResource(R.string.checkout_wallet_used), "-₹$walletUsed", Teal)
                    }

                    Divider(Modifier.padding(vertical = 10.dp), color = BorderGray)

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.cart_total),
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "₹${trimAmount(payableTotal)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Teal
                        )
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Dimens.ShapeMd,
                    color = CoralSurface
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        color = CoralDark,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Surface(
            color = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (selected == null && addresses.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.checkout_select_address),
                        fontSize = 12.sp,
                        color = Coral
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (belowMinimumOrder) {
                    Text(
                        text = stringResource(
                            R.string.checkout_minimum_order,
                            trimAmount(minOrderValue - subtotal),
                            trimAmount(minOrderValue)
                        ),
                        fontSize = 12.sp,
                        color = Coral
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        selected?.let {
                            // 0.0 is the column default for an address that was
                            // typed by hand rather than detected — treated as
                            // "no fix" rather than a real coordinate near
                            // (0°, 0°).
                            val lat = it.latitude.takeIf { v -> v != 0.0 }
                            val lng = it.longitude.takeIf { v -> v != 0.0 }
                            if (payOnline && onlinePayAvailable) {
                                onPayOnline(it.fullAddress, lat, lng, walletUsed)
                            } else {
                                onPlaceOrder(it.fullAddress, lat, lng, walletUsed)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = Dimens.ShapeMd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BlinkitGreen,
                        disabledContainerColor = BorderGray
                    ),
                    // An order with no address is not deliverable, and one
                    // below the store's minimum is not worth fulfilling, so
                    // the button stays disabled until both are satisfied.
                    enabled = !isLoading && selected != null && cartItems.isNotEmpty() && !belowMinimumOrder
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (payOnline && onlinePayAvailable) {
                                stringResource(R.string.checkout_pay_online, trimAmount(payableTotal))
                            } else {
                                stringResource(R.string.checkout_place_order, trimAmount(payableTotal))
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutHeader(onBack: () -> Unit) {
    ScreenHeader(title = stringResource(R.string.checkout_title), onBack = onBack)
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Dimens.ShapeMd,
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        content = content
    )
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(Dimens.ShapeSm)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun AddressOption(address: Address, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dugguClickable { onSelect() },
        shape = Dimens.ShapeMd,
        color = if (selected) TealSurface else SurfaceWhite,
        shadowElevation = if (selected) 0.dp else 2.dp,
        border = if (selected) BorderStroke(1.5.dp, Teal) else null
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle
                              else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Teal else TextLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(address.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(address.fullAddress, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun PaymentOption(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    selectable: Boolean,
    onSelect: () -> Unit
) {
    Panel(modifier = if (selectable) Modifier.dugguClickable { onSelect() } else Modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(icon, tint)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Teal else TextLight,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
