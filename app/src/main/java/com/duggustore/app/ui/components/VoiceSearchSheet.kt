package com.duggustore.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.platform.VoiceSearchController
import com.duggustore.app.platform.VoiceState
import com.duggustore.app.ui.theme.*

/**
 * The app's own listening sheet.
 *
 * Recognition runs in-process, so nothing here is Google's UI: the mark, the
 * teal, the rounded sheet and the copy are the store's, and what the user says
 * appears as they say it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSearchSheet(controller: VoiceSearchController) {
    if (!controller.isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { controller.close() },
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (controller.state) {
                    VoiceState.Listening -> "Listening…"
                    VoiceState.Working -> "One moment"
                    VoiceState.NeedsPermission -> "Microphone needed"
                    is VoiceState.Failed -> "Try again"
                    VoiceState.Idle -> ""
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = when (val state = controller.state) {
                    VoiceState.Listening -> "Say a product name"
                    VoiceState.Working -> "Working out what you said"
                    VoiceState.NeedsPermission ->
                        "Voice search needs access to the microphone"
                    is VoiceState.Failed -> state.message
                    VoiceState.Idle -> ""
                },
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            MicOrb(state = controller.state, level = controller.level)

            Spacer(Modifier.height(24.dp))

            // Held at a fixed height so the sheet does not jump as words arrive.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = controller.partial.ifBlank {
                        if (controller.state == VoiceState.Listening) "…" else ""
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (controller.partial.isBlank()) TextLight else TextPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 25.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            when (controller.state) {
                VoiceState.NeedsPermission -> SheetAction(
                    label = "Allow microphone",
                    onClick = { controller.retryPermission() }
                )
                is VoiceState.Failed -> SheetAction(
                    label = "Try again",
                    icon = true,
                    onClick = { controller.open() }
                )
                else -> Text(
                    text = "Tap outside to cancel",
                    fontSize = 12.sp,
                    color = TextLight
                )
            }
        }
    }
}

/** Mic on a teal disc, with a ring that grows with what the microphone hears. */
@Composable
private fun MicOrb(state: VoiceState, level: Float) {
    val listening = state == VoiceState.Listening

    // The ring follows the voice; the slow pulse underneath keeps the orb alive
    // during a silence so it does not look frozen.
    val ring by animateFloatAsState(
        targetValue = if (listening) 1f + level * 0.55f else 1f,
        animationSpec = tween(140),
        label = "ring"
    )
    val transition = rememberInfiniteTransition(label = "orb")
    val idlePulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .scale(ring)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .size(104.dp)
                .scale(idlePulse)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.18f))
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        is VoiceState.Failed, VoiceState.NeedsPermission -> Coral
                        else -> Teal
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (state == VoiceState.Working) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                // One glyph throughout; the disc turning coral is what says
                // something went wrong.
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, icon: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Teal)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
