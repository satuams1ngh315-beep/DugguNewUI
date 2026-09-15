package com.duggustore.app.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.ui.theme.*

/**
 * Shown while the stored session is being restored, so the app does not flash the
 * login screen before it knows whether someone is already signed in.
 */
@Composable
fun SplashScreen(
    error: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val transition = rememberInfiniteTransition(label = "splash")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val glow by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = "Duggu Store",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(450f / 484f)
                .scale(scale)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // The logo is just the mark now — the name is set as text rather
        // than baked into the image, same as AppLogo() on the auth screens.
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Groceries delivered fast",
            fontSize = 14.sp,
            color = TextSecondary,
            modifier = Modifier.alpha(glow + 0.4f)
        )

        Spacer(modifier = Modifier.height(40.dp))

        if (error != null && onRetry != null) {
            Text(
                text = error,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) {
                Text("Retry", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Teal,
                strokeWidth = 2.5.dp
            )
        }
    }
}
