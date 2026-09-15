package com.duggustore.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.ui.components.AppLogo
import com.duggustore.app.ui.theme.*

private data class WelcomeSlide(
    val icon: ImageVector,
    val accent: Color,
    val title: String,
    val body: String
)

/**
 * The three things worth knowing before signing up, one per screen —
 * swipeable, skippable, and shown only on the first run. Without it the app
 * opened straight onto a login form that never explained what it was for or
 * why someone would pick "Sell" or "Deliver" over "Shop" at sign-up.
 */
@Composable
fun WelcomeScreen(
    onFinish: () -> Unit,
    onLogIn: () -> Unit
) {
    val slides = listOf(
        WelcomeSlide(
            icon = Icons.Default.ShoppingBasket,
            accent = Teal,
            title = stringResource(R.string.welcome_slide1_title),
            body = stringResource(R.string.welcome_slide1_body)
        ),
        WelcomeSlide(
            icon = Icons.Default.LocalShipping,
            accent = Orange,
            title = stringResource(R.string.welcome_slide2_title),
            body = stringResource(R.string.welcome_slide2_body)
        ),
        WelcomeSlide(
            icon = Icons.Default.Storefront,
            accent = Violet,
            title = stringResource(R.string.welcome_slide3_title),
            body = stringResource(R.string.welcome_slide3_body)
        )
    )

    var index by rememberSaveable { mutableStateOf(0) }
    var movingForward by remember { mutableStateOf(true) }
    val isLast = index == slides.lastIndex

    fun goTo(next: Int) {
        if (next < 0 || next > slides.lastIndex) return
        movingForward = next > index
        index = next
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppLogo(size = 56)
            Spacer(Modifier.weight(1f))
            if (!isLast) {
                Text(
                    text = stringResource(R.string.welcome_skip),
                    modifier = Modifier.clickable { onFinish() },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
            }
        }

        var dragged by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(index) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // A short flick is enough; the buttons stay the
                            // primary way through for anyone who doesn't swipe.
                            if (dragged < -60f) goTo(index + 1)
                            if (dragged > 60f) goTo(index - 1)
                            dragged = 0f
                        },
                        onDragCancel = { dragged = 0f },
                        onHorizontalDrag = { _, delta -> dragged += delta }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = slideStepTransition(movingForward),
                label = "welcome-slide"
            ) { i ->
                val slide = slides[i]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .background(slide.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            slide.icon,
                            contentDescription = null,
                            tint = slide.accent,
                            modifier = Modifier.size(84.dp)
                        )
                    }
                    Spacer(Modifier.height(36.dp))
                    Text(
                        text = slide.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = slide.body,
                        fontSize = 15.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            slides.indices.forEach { i ->
                val selected = i == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (selected) 22.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (selected) Teal else BorderGray)
                        .clickable { goTo(i) }
                )
            }
        }

        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
            Button(
                onClick = { if (isLast) onFinish() else goTo(index + 1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) {
                Text(
                    text = if (isLast) stringResource(R.string.welcome_get_started) else stringResource(R.string.welcome_next),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.welcome_have_account), fontSize = 14.sp, color = TextSecondary)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.welcome_log_in),
                    modifier = Modifier.clickable { onLogIn() },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Teal
                )
            }
        }
    }
}
