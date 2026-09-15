package com.duggustore.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.data.model.DbHealth
import com.duggustore.app.data.model.TableStat
import com.duggustore.app.ui.components.DashboardPanel
import com.duggustore.app.ui.theme.CoralDark
import com.duggustore.app.ui.theme.CoralSurface
import com.duggustore.app.ui.theme.Dimens
import com.duggustore.app.ui.theme.Orange
import com.duggustore.app.ui.theme.SurfaceMuted
import com.duggustore.app.ui.theme.Teal
import com.duggustore.app.ui.theme.TealSurface
import com.duggustore.app.ui.theme.TextLight
import com.duggustore.app.ui.theme.TextPrimary
import com.duggustore.app.ui.theme.TextSecondary

/**
 * A read-only window on the database: headline counters, the queues that need
 * a human, and a row count per table.
 *
 * Everything here comes from two SECURITY DEFINER RPCs that check the caller is
 * an admin (supabase_admin_stats.sql). Nothing on this screen writes, so it is
 * safe to leave in a shipped build.
 */
@Composable
fun AdminDatabaseScreen(
    health: DbHealth?,
    tableStats: List<TableStat>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onClearError: () -> Unit
) {
    // First open pulls the figures; afterwards the admin refreshes by hand so
    // a tab switch doesn't re-scan every table.
    LaunchedEffect(Unit) {
        if (health == null && tableStats.isEmpty()) onRefresh()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (error != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.SpaceLg, vertical = Dimens.SpaceXs),
                shape = Dimens.ShapeSm,
                color = CoralSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(error, modifier = Modifier.weight(1f), color = CoralDark, fontSize = 12.sp)
                    Text(
                        "Dismiss",
                        color = CoralDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = Dimens.SpaceSm)
                            .clickable { onClearError() }
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(Dimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Database",
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Teal
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(Dimens.ShapeSm)
                                .background(TealSurface)
                                .clickable { onRefresh() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Teal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teal)
                        }
                    }
                }
            }

            if (health != null) {
                item { SectionLabel("Today") }
                item {
                    MetricRow(
                        listOf(
                            Triple("Signups", "${health.signupsToday}", Teal),
                            Triple("Orders", "${health.ordersToday}", Orange),
                            Triple("Revenue", "₹${health.revenueToday.toLong()}", Teal)
                        )
                    )
                }

                item { SectionLabel("Last 7 days") }
                item {
                    MetricRow(
                        listOf(
                            Triple("Signups", "${health.signupsWeek}", Teal),
                            Triple("Orders", "${health.ordersWeek}", Orange),
                            Triple("Revenue", "₹${health.revenueWeek.toLong()}", Teal)
                        )
                    )
                }

                item { SectionLabel("Needs attention") }
                item {
                    // Zero is the good case, so these read as plain grey until
                    // there is actually something in the queue.
                    AttentionGrid(
                        listOf(
                            "Sellers awaiting review" to health.pendingSellers,
                            "Riders awaiting review" to health.pendingPartners,
                            "Open order issues" to health.openIssues,
                            "Orders stuck over a day" to health.stuckOrders,
                            "Active products out of stock" to health.outOfStock,
                            "Seller role, no seller record" to health.orphanSellers
                        )
                    )
                }
            }

            if (tableStats.isNotEmpty()) {
                item { SectionLabel("Tables (${tableStats.size})") }
                items(tableStats, key = { it.tableName }) { TableStatRow(it) }
            }

            if (health == null && tableStats.isEmpty() && !isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Storage, null, tint = TextLight, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(Dimens.SpaceSm))
                            Text("No stats loaded", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = Dimens.SpaceXs),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary
    )
}

@Composable
private fun MetricRow(metrics: List<Triple<String, String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
        metrics.forEach { (label, value, tint) ->
            DashboardPanel(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp)) {
                    Text(
                        value,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AttentionGrid(items: List<Pair<String, Int>>) {
    DashboardPanel {
        Column(modifier = Modifier.padding(vertical = Dimens.SpaceXs)) {
            items.forEach { (label, count) ->
                val urgent = count > 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = if (urgent) TextPrimary else TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (urgent) CoralSurface else SurfaceMuted)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "$count",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (urgent) CoralDark else TextLight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableStatRow(stat: TableStat) {
    DashboardPanel {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stat.tableName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stat.lastCreated?.let { "Last write ${it.take(10)}" } ?: "No timestamp column",
                    fontSize = 11.sp,
                    color = TextLight
                )
            }
            Box(
                modifier = Modifier
                    .clip(Dimens.ShapeSm)
                    .background(if (stat.rowCount > 0) TealSurface else SurfaceMuted)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    formatCount(stat.rowCount),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (stat.rowCount > 0) Teal else TextLight
                )
            }
        }
    }
}

/** Keeps wide counts from pushing the table name out of the row. */
private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
    else -> "$n"
}
