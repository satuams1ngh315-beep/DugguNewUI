package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.duggustore.app.data.model.VerificationStatus
import com.duggustore.app.ui.theme.*

/**
 * The thin progress bar + "Step X of Y" chrome shared by every step-by-step
 * wizard in the app (sign-up, seller onboarding, rider onboarding), so all
 * three read as one consistent pattern rather than three similar-looking
 * ones.
 */
@Composable
fun StepProgressBar(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index <= current) Teal else BorderGray)
            )
        }
    }
}

/** A step's title + one-line reason it's being asked, above that step's field(s). */
@Composable
fun StepHeading(title: String, subtitle: String) {
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)
    Spacer(Modifier.height(22.dp))
}

/**
 * Fills the blank space a one-field step leaves below the keyboard on tall
 * screens, and doubles as a small bit of reassurance about why we're asking.
 */
@Composable
fun StepIllustration(icon: ImageVector, tint: Color, caption: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = caption,
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** One document's row in the onboarding form: label, its current status, and an upload/re-upload action. */
@Composable
fun DocumentUploadRow(
    label: String,
    docStatus: String?,
    isUploading: Boolean,
    onPick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceWhite,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (tint, icon) = when (docStatus) {
                "VERIFIED" -> SuccessGreen to Icons.Default.CheckCircle
                "REJECTED" -> Coral to Icons.Default.ReportProblem
                null -> TextLight to Icons.Default.InsertDriveFile
                else -> Orange to Icons.Default.HourglassTop
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    text = when (docStatus) {
                        "VERIFIED" -> "Verified"
                        "REJECTED" -> "Rejected — please re-upload"
                        "PENDING" -> "Uploaded, awaiting review"
                        else -> "Not uploaded yet"
                    },
                    fontSize = 12.sp,
                    color = tint
                )
            }
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Teal)
            } else {
                TextButton(onClick = onPick) {
                    Icon(Icons.Default.CloudUpload, null, tint = Teal, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (docStatus == null) "Upload" else "Replace", color = Teal, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Full-screen notice shown while an application is under review or the
 * account has been suspended — the only two statuses that keep the seller
 * or rider out of their dashboard without also being at the onboarding
 * form itself (PENDING_VERIFICATION and REJECTED both show the form
 * directly, with REJECTED just carrying a banner about why).
 */
@Composable
fun OnboardingStatusScreen(
    status: VerificationStatus,
    rejectionReason: String?,
    onSignOut: () -> Unit
) {
    val (tint, title, subtitle) = when (status) {
        VerificationStatus.SUSPENDED -> Triple(
            Coral,
            "Account suspended",
            rejectionReason?.takeIf { it.isNotBlank() }
                ?: "Your account has been suspended. Contact support for more information."
        )
        else -> Triple(
            Orange,
            "Application under review",
            "We're checking your details and documents. This usually takes a couple of days — you'll be able to get started as soon as it's approved."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.HourglassTop, null, tint = tint, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Sign out", color = TextSecondary)
        }
    }
}
