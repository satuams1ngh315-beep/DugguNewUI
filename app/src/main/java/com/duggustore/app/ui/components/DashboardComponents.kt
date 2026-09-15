package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.ui.theme.*

/**
 * Teal band shared by the seller, delivery and admin dashboards: the title, a
 * sign-out control and a row of headline figures.
 */
@Composable
fun DashboardHeader(
    title: String,
    subtitle: String,
    stats: List<Pair<String, String>>,
    onSignOut: () -> Unit,
    issuesBadgeCount: Int = 0,
    onIssuesClick: (() -> Unit)? = null
) {
    Surface(color = Teal) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }
                if (onIssuesClick != null) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable { onIssuesClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ReportProblem,
                                contentDescription = "Reported issues",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        if (issuesBadgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                    .clip(CircleShape)
                                    .background(AccentRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (issuesBadgeCount > 9) "9+" else "$issuesBadgeCount",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { onSignOut() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "Sign out",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.forEach { (label, value) ->
                        StatCard(label = label, value = value, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }
}

/** Pill tabs on a white strip, in place of the underlined Material TabRow. */
@Composable
fun DashboardTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Surface(color = SurfaceWhite, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val active = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Teal else SurfaceMuted)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) Color.White else TextSecondary
                    )
                }
            }
        }
    }
}

/** Rounded white surface used for every row and block on the dashboards. */
@Composable
fun DashboardPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        content = content
    )
}

@Composable
fun DashboardEmpty(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = Teal,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            modifier = Modifier.padding(horizontal = 40.dp),
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** Compact action used on the dashboard rows (Accept, Reject, Picked up...). */
@Composable
fun DashboardAction(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) Color.White else color
        )
    }
}
