package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.duggustore.app.data.model.CartItem
import com.duggustore.app.ui.theme.Background
import com.duggustore.app.ui.theme.BorderGray
import com.duggustore.app.ui.theme.CoralDark
import com.duggustore.app.ui.theme.CoralSurface
import com.duggustore.app.ui.theme.DividerGray
import com.duggustore.app.ui.theme.DisabledGray
import com.duggustore.app.ui.theme.MyntraPink
import com.duggustore.app.ui.theme.MyntraPinkOutline
import com.duggustore.app.ui.theme.MyntraPinkSurface
import com.duggustore.app.ui.theme.SuccessGreen
import com.duggustore.app.ui.theme.SurfaceMuted
import com.duggustore.app.ui.theme.SurfaceWhite
import com.duggustore.app.ui.theme.TextLight
import com.duggustore.app.ui.theme.TextPrimary
import com.duggustore.app.ui.theme.TextSecondary

/**
 * Checkout, without leaving the bag.
 *
 * The bag's own button used to hand the shopper to a whole separate checkout
 * page to answer two questions — where is this going, and how am I paying —
 * when the cart already knows both: the address is the saved default and the
 * payment method is cash on delivery, the only one this app takes. Those two
 * facts are shown here instead of asked for, so the common case (the address
 * you used last time, pay on delivery) is one tap from the bag. Anything that
 * genuinely needs the full page — adding a new address, a coupon — is one tap
 * away rather than gone: every path on this sheet still lands on the same
 * `placeOrder`, so an order placed here is identical to one placed there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCheckoutSheet(
    cartItems: List<CartItem>,
    addresses: List<Address>,
    subtotal: Double,
    deliveryFee: Double,
    total: Double,
    savings: Double,
    couponApplied: Boolean,
    couponDiscount: Double,
    belowMinimumOrder: Boolean,
    minOrderValue: Double,
    isLoading: Boolean,
    error: String? = null,
    walletBalance: Int = 0,
    onManageAddresses: () -> Unit,
    onPlaceOrder: (deliveryAddress: String, latitude: Double?, longitude: Double?, walletAmount: Int) -> Unit,
    onOpenFullCheckout: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Preselected exactly like the full page does it, so the two agree about
    // which address is "the" one: the default, or the only one there is.
    var selectedId by remember(addresses) {
        mutableStateOf(
            addresses.firstOrNull { it.isDefault }?.id ?: addresses.firstOrNull()?.id ?: ""
        )
    }
    val selected = addresses.firstOrNull { it.id == selectedId }

    // Off by default: spending the wallet is a decision, and a checkout that
    // silently drained it would be a nasty surprise.
    var useWallet by remember { mutableStateOf(false) }
    val maxWalletUsable = minOf(walletBalance, total.toInt())
    val walletUsed = if (useWallet) maxWalletUsable else 0
    val payable = (total - walletUsed).coerceAtLeast(0.0)

    val itemCount = cartItems.sumOf { it.quantity }
    val canPlaceOrder = selected != null && !belowMinimumOrder && !isLoading

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Capped rather than left to grow: the bill is a fixed few
                // rows, and an address list long enough to fill the screen
                // should scroll inside the sheet, not push it off the top.
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.quick_checkout_title),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.quick_checkout_subtitle),
                    fontSize = 12.5.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))

            SheetSectionLabel(stringResource(R.string.quick_checkout_deliver_to))

            if (addresses.isEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MyntraPinkSurface)
                            .border(1.dp, MyntraPinkOutline, RoundedCornerShape(14.dp))
                            .dugguClickableFlat(onManageAddresses)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.quick_checkout_add_address),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MyntraPink
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.quick_checkout_no_address),
                        fontSize = 12.sp,
                        color = TextLight
                    )
                }
            } else {
                addresses.forEach { address ->
                    AddressRow(
                        address = address,
                        selected = address.id == selectedId,
                        onSelect = { selectedId = address.id }
                    )
                }
                Text(
                    text = stringResource(R.string.checkout_manage_addresses),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .dugguClickableFlat(onManageAddresses),
                    color = MyntraPink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(18.dp))

            SheetSectionLabel(stringResource(R.string.quick_checkout_summary))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Background)
                    .padding(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_checkout_items, itemCount),
                    fontSize = 12.5.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                QuickBillRow(
                    label = stringResource(R.string.cart_subtotal),
                    value = "₹${trimAmount(subtotal)}"
                )
                QuickBillRow(
                    label = stringResource(R.string.cart_delivery_fee),
                    value = if (deliveryFee <= 0.0) stringResource(R.string.cart_free)
                            else "₹${trimAmount(deliveryFee)}",
                    valueColor = if (deliveryFee <= 0.0) SuccessGreen else TextPrimary
                )
                if (couponApplied) {
                    QuickBillRow(
                        label = stringResource(R.string.cart_coupon_discount),
                        value = "-₹${trimAmount(couponDiscount)}",
                        valueColor = SuccessGreen
                    )
                }
                if (savings > 0) {
                    QuickBillRow(
                        label = stringResource(R.string.cart_you_save),
                        value = "-₹${trimAmount(savings)}",
                        valueColor = SuccessGreen
                    )
                }
                if (walletUsed > 0) {
                    QuickBillRow(
                        label = stringResource(R.string.checkout_wallet_used),
                        value = "-₹$walletUsed",
                        valueColor = SuccessGreen
                    )
                }

                Divider(
                    modifier = Modifier.padding(vertical = 9.dp),
                    color = DividerGray
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.quick_checkout_to_pay),
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "₹${trimAmount(payable)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (walletBalance > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceMuted)
                        .dugguClickableFlat { useWallet = !useWallet }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.quick_checkout_wallet),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(
                                R.string.quick_checkout_wallet_available,
                                walletBalance
                            ),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = useWallet,
                        onCheckedChange = { useWallet = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MyntraPink
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // Stated rather than offered: cash on delivery is the only method
            // this app takes, and a payment picker with one option is a
            // question with one answer.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.quick_checkout_cod),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            if (belowMinimumOrder) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.cart_minimum_order,
                        trimAmount(minOrderValue - subtotal),
                        trimAmount(minOrderValue)
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    fontSize = 12.5.sp,
                    color = CoralDark
                )
            }

            // placeOrder's own failures (two sellers in one cart, an address
            // that went away, a rejected write) surface here, in the sheet
            // that asked for the decision.
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CoralSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(text = error, color = CoralDark, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (canPlaceOrder) MyntraPink else DisabledGray)
                    .then(
                        if (canPlaceOrder) {
                            Modifier.dugguClickableFlat {
                                selected?.let { address ->
                                    onPlaceOrder(
                                        address.fullAddress,
                                        address.latitude.takeIf { it != 0.0 },
                                        address.longitude.takeIf { it != 0.0 },
                                        walletUsed
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.quick_checkout_place,
                            trimAmount(payable)
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .dugguClickableFlat(onOpenFullCheckout),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.quick_checkout_review),
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * One saved address, tappable across its whole row. Uses the same radio
 * language as the full checkout page so picking an address looks the same
 * wherever it is done.
 */
@Composable
private fun AddressRow(
    address: Address,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(shape)
            .background(SurfaceWhite)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) MyntraPink else BorderGray,
                shape = shape
            )
            .dugguClickableFlat(onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MyntraPink else TextLight,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = address.label.ifBlank { stringResource(R.string.quick_checkout_address_fallback) },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (address.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceMuted)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.quick_checkout_default),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = address.fullAddress,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
}

/** Label left, amount right — the same shape the bag and the full checkout use. */
@Composable
private fun QuickBillRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

