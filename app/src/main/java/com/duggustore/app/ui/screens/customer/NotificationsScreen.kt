package com.duggustore.app.ui.screens.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.data.model.StoreNotification
import com.duggustore.app.ui.components.DashboardEmpty
import com.duggustore.app.R
import com.duggustore.app.ui.components.ScreenHeader
import com.duggustore.app.ui.components.dugguClickable
import com.duggustore.app.ui.theme.*

@Composable
fun NotificationsScreen(
    notifications: List<StoreNotification>,
    readIds: Set<String>,
    onNotificationClick: (StoreNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onBack: () -> Unit
) {
    val unreadCount = notifications.count { it.id !in readIds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        ScreenHeader(
            title = stringResource(R.string.notifications_title),
            subtitle = if (notifications.size == 1) "1 update" else "${notifications.size} updates",
            onBack = onBack,
            actions = {
                // Only worth showing once there's something it would change —
                // an always-on button next to zero unread reads as broken.
                if (unreadCount > 0) {
                    TextButton(onClick = onMarkAllRead) {
                        Text(
                            stringResource(R.string.notifications_mark_all_read),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        )

        if (notifications.isEmpty()) {
            DashboardEmpty(
                icon = Icons.Default.NotificationsNone,
                title = stringResource(R.string.notifications_empty_title),
                subtitle = stringResource(R.string.notifications_empty_sub)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        isRead = notification.id in readIds,
                        onClick = { onNotificationClick(notification) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: StoreNotification, isRead: Boolean, onClick: () -> Unit) {
    val (icon, tint) = iconFor(notification.kind)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dugguClickable { onClick() },
        shape = Dimens.ShapeMd,
        // A read card sinks back into the page (muted fill, no shadow) so an
        // unread one is the thing that visibly stands out, rather than the
        // other way round.
        color = if (isRead) SurfaceMuted else SurfaceWhite,
        shadowElevation = if (isRead) 0.dp else 2.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (isRead) 0.08f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (isRead) tint.copy(alpha = 0.6f) else tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notification.title,
                        fontSize = 15.sp,
                        fontWeight = if (isRead) FontWeight.Medium else FontWeight.Bold,
                        color = if (isRead) TextSecondary else TextPrimary
                    )
                    if (!isRead) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Coral)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = notification.body,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )
                if (notification.timestamp.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = notification.timestamp.take(10),
                        fontSize = 11.sp,
                        color = TextLight
                    )
                }
            }
        }
    }
}

private fun iconFor(kind: StoreNotification.Kind): Pair<ImageVector, Color> = when (kind) {
    StoreNotification.Kind.Placed -> Icons.Default.Receipt to Orange
    StoreNotification.Kind.Confirmed -> Icons.Default.CheckCircle to BlinkitGreen
    StoreNotification.Kind.Preparing -> Icons.Default.Inventory to Orange
    StoreNotification.Kind.ReadyForPickup -> Icons.Default.ShoppingBag to Orange
    StoreNotification.Kind.OutForDelivery -> Icons.Default.LocalShipping to BlinkitGreen
    StoreNotification.Kind.Delivered -> Icons.Default.CheckCircle to SuccessGreen
    StoreNotification.Kind.Cancelled -> Icons.Default.Cancel to Coral
}
