package com.duggustore.app.ui.screens.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.data.model.UserProfile
import com.duggustore.app.R
import com.duggustore.app.ui.components.dugguClickable
import com.duggustore.app.ui.theme.*

@Composable
fun AccountScreen(
    user: UserProfile?,
    onOrdersClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onAddressesClick: () -> Unit,
    onWalletClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    // A Box rather than letting the scrolling Column alone fill the screen —
    // otherwise a short menu (as this one is) leaves the footer floating in
    // whatever blank space is left instead of sitting at the true bottom.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            AccountHeader(user = user, onBack = onBack)

            Spacer(Modifier.height(18.dp))

            MenuGroup {
                AccountMenuItem(
                    icon = Icons.Default.Receipt,
                    title = stringResource(R.string.account_my_orders),
                    subtitle = stringResource(R.string.account_my_orders_sub),
                    onClick = onOrdersClick
                )
                Divider(color = BorderGray, modifier = Modifier.padding(start = 68.dp))
                AccountMenuItem(
                    icon = Icons.Default.Favorite,
                    title = stringResource(R.string.nav_favourites),
                    subtitle = stringResource(R.string.account_favourites_sub),
                    tint = Coral,
                    onClick = onFavoritesClick
                )
                Divider(color = BorderGray, modifier = Modifier.padding(start = 68.dp))
                AccountMenuItem(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.account_addresses),
                    subtitle = stringResource(R.string.account_addresses_sub),
                    tint = Orange,
                    onClick = onAddressesClick
                )
                Divider(color = BorderGray, modifier = Modifier.padding(start = 68.dp))
                AccountMenuItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = stringResource(R.string.account_wallet),
                    subtitle = stringResource(R.string.account_wallet_sub),
                    tint = Teal,
                    onClick = onWalletClick
                )
                Divider(color = BorderGray, modifier = Modifier.padding(start = 68.dp))
                AccountMenuItem(
                    icon = Icons.Default.CardGiftcard,
                    title = stringResource(R.string.account_refer),
                    subtitle = stringResource(R.string.account_refer_sub),
                    tint = Orange,
                    // The referral code and its terms live on the wallet screen
                    // rather than a page of their own.
                    onClick = onWalletClick
                )
                Divider(color = BorderGray, modifier = Modifier.padding(start = 68.dp))
                AccountMenuItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.account_notifications),
                    subtitle = stringResource(R.string.account_notifications_sub),
                    tint = InfoBlue,
                    onClick = onNotificationsClick
                )
                Divider(color = BorderGray, modifier = Modifier.padding(start = 68.dp))
                AccountMenuItem(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.account_settings),
                    subtitle = stringResource(R.string.account_settings_sub),
                    tint = TextSecondary,
                    onClick = onSettingsClick
                )
            }

            Spacer(Modifier.height(16.dp))

            MenuGroup {
                AccountMenuItem(
                    icon = Icons.Default.Logout,
                    title = stringResource(R.string.account_sign_out),
                    subtitle = stringResource(R.string.account_sign_out_sub),
                    tint = Coral,
                    showChevron = false,
                    onClick = onSignOut
                )
            }

            // Leaves room so the list never sits flush against the pinned
            // footer below it.
            Spacer(Modifier.height(72.dp))
        }

        Text(
            text = "Duggu Store",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            fontSize = 12.sp,
            color = TextLight,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun AccountHeader(user: UserProfile?, onBack: () -> Unit) {
    // How far the card rises up into the teal band — a fixed visual
    // overlap, kept independent of the card's own content-driven height
    // (a longer name, or the phone line appearing) so it can never grow
    // tall enough to reach up into the back button.
    val cardOverlap = 40.dp
    val bandClearance = 16.dp

    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .statusBarsPadding()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.common_back), tint = TextPrimary)
            }
            Spacer(Modifier.height(cardOverlap + bandClearance))
        }

        // The card straddles the band, as the sheets do on the other screens.
        // The `layout` modifier reports the card's height minus the overlap
        // and then draws it shifted up by that same amount, so the content
        // below the card slides up to meet it instead of leaving a gap.
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val overlapPx = cardOverlap.roundToPx()
                    layout(placeable.width, placeable.height - overlapPx) {
                        placeable.placeRelative(0, -overlapPx)
                    }
                },
            shape = Dimens.ShapeLg,
            color = SurfaceWhite,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(BrandPrimarySurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user?.fullName?.trim()?.firstOrNull()?.uppercase() ?: "U",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPrimaryDark
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.fullName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.account_user),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    // The profiles table carries no email, only the phone.
                    val phone = user?.phone?.takeIf { it.isNotBlank() }
                    if (phone != null) {
                        Text(text = phone, fontSize = 13.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = Dimens.ShapeSm, color = OrangeSurface) {
                        Text(
                            text = (user?.role ?: "customer").uppercase(),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangeDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = Dimens.ShapeMd,
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun AccountMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = Teal,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dugguClickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(Dimens.ShapeSm)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        if (showChevron) {
            Icon(Icons.Default.ChevronRight, null, tint = TextLight)
        }
    }
}
