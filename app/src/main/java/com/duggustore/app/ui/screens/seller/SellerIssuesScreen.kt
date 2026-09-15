package com.duggustore.app.ui.screens.seller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.data.model.OrderIssue
import com.duggustore.app.ui.theme.*

@Composable
fun SellerIssuesScreen(
    issues: List<OrderIssue>,
    onResolve: (issueId: String, approve: Boolean, refundAmount: Int) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        Surface(color = Teal) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text("Reported issues", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        IssuesList(issues = issues, onResolve = onResolve, modifier = Modifier.weight(1f))
    }
}

/**
 * The open/resolved issue list on its own, with no header — shared by
 * [SellerIssuesScreen] (which adds its own back-button header) and the
 * admin dashboard's Orders tab (which sits under the dashboard's header
 * and a segmented all-orders/issues switch instead).
 */
@Composable
fun IssuesList(
    issues: List<OrderIssue>,
    onResolve: (issueId: String, approve: Boolean, refundAmount: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val open = issues.filter { it.status == "open" }
    val past = issues.filter { it.status != "open" }

    if (issues.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.ReportProblem, null, tint = TextLight, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("No issues reported", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                "Customer complaints on orders show up here",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 4.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (open.isNotEmpty()) {
                items(open, key = { it.id }) { issue ->
                    OpenIssueCard(issue = issue, onResolve = onResolve)
                }
            }
            if (past.isNotEmpty()) {
                item {
                    Text(
                        "Resolved",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(past, key = { it.id }) { issue -> PastIssueCard(issue) }
            }
        }
    }
}

@Composable
private fun OpenIssueCard(issue: OrderIssue, onResolve: (String, Boolean, Int) -> Unit) {
    var refundText by remember(issue.id) { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Order #${issue.orderId.takeLast(8).uppercase()}",
                fontSize = 12.sp,
                color = TextLight
            )
            Spacer(Modifier.height(4.dp))
            Text(issue.reason, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            if (issue.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(issue.description, fontSize = 13.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = refundText,
                onValueChange = { input -> if (input.all { it.isDigit() }) refundText = input },
                label = { Text("Refund amount (₹, optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onResolve(issue.id, false, 0) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)
                ) { Text("Reject") }
                Button(
                    onClick = { onResolve(issue.id, true, refundText.toIntOrNull() ?: 0) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) { Text("Approve") }
            }
        }
    }
}

@Composable
private fun PastIssueCard(issue: OrderIssue) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(issue.reason, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    text = "Order #${issue.orderId.takeLast(8).uppercase()}",
                    fontSize = 11.sp,
                    color = TextLight
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (issue.status == "resolved") SuccessGreen.copy(alpha = 0.15f) else CoralSurface
            ) {
                Text(
                    text = if (issue.status == "resolved") "Refunded ₹${issue.refundAmount}" else "Rejected",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (issue.status == "resolved") SuccessGreen else CoralDark
                )
            }
        }
    }
}
