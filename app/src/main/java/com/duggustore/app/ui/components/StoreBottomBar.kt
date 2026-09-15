package com.duggustore.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.ui.theme.*

/** One destination in the bottom bar. */
data class BottomNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/** Legacy — kept for binary compatibility. */
data class BottomBarCentre(
    val count: Int,
    val selected: Boolean,
    val onClick: () -> Unit
)

/** Height of the bar itself, above the system navigation inset. */
val StoreBottomBarHeight = 62.dp

/**
 * Myntra-style bottom navigation: a plain white bar with a hairline top
 * divider, one outlined icon per tab that fills with brand pink when the tab
 * is active, and an optional pink count badge (used on the Bag tab).
 */
@Composable
fun StoreBottomBar(
    items: List<BottomNavItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    centre: BottomBarCentre? = null,
    badgeCounts: Map<String, Int> = emptyMap()
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceWhite,
        shadowElevation = 10.dp
    ) {
        Column {
            // Hairline top divider instead of the old gradient strip.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerGray)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(StoreBottomBarHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    PremiumNavItem(
                        item = item,
                        selected = item.key == selectedKey,
                        badgeCount = badgeCounts[item.key] ?: 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(item.key) }
                    )
                }
            }
        }
    }
}

/** The filled counterpart of an outlined tab icon, when one exists. */
private fun filledVersion(icon: ImageVector): ImageVector? = when (icon) {
    Icons.Outlined.Home -> Icons.Filled.Home
    Icons.Outlined.Person -> Icons.Filled.Person
    Icons.Outlined.GridView -> Icons.Filled.GridView
    Icons.Outlined.FavoriteBorder -> Icons.Filled.Favorite
    Icons.Outlined.LocalMall -> Icons.Filled.LocalMall
    else -> null
}

@Composable
private fun PremiumNavItem(
    item: BottomNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    // Subtle scale-up of the active icon.
    val iconSize by animateDpAsState(
        targetValue = if (selected) 23.dp else 21.dp,
        animationSpec = androidx.compose.animation.core.tween(180)
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = MyntraPink.copy(alpha = 0.15f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val activeColor = MyntraPink
            val icon = if (selected) filledVersion(item.icon) ?: item.icon else item.icon

            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.label,
                    tint = if (selected) activeColor else TextLight,
                    modifier = Modifier.size(iconSize)
                )
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = 7.dp, y = (-5).dp)
                            .defaultMinSize(minWidth = 15.dp, minHeight = 15.dp)
                            .clip(CircleShape)
                            .background(MyntraPink)
                            .padding(horizontal = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (badgeCount > 9) "9+" else "$badgeCount",
                            color = Color.White,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.label,
                fontSize = 10.sp,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) activeColor else TextLight
            )
        }
    }
}
