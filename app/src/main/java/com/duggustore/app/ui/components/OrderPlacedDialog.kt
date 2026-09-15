package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.duggustore.app.R
import com.duggustore.app.ui.theme.MyntraPink
import com.duggustore.app.ui.theme.MyntraPinkSurface
import com.duggustore.app.ui.theme.SurfaceWhite
import com.duggustore.app.ui.theme.TextPrimary
import com.duggustore.app.ui.theme.TextSecondary

/**
 * What a shopper sees the moment an order lands.
 *
 * There were two copies of this, one on the bag route and one on the
 * checkout route, both built from an AlertDialog with its title, body and
 * two buttons written out in English — so the confirmation was the one
 * screen in the flow that never changed language, and the two copies could
 * drift apart. It is one composable now, and it speaks the same language as
 * the rest of the app: white card, the tick in the pink the shopper has
 * been tapping all along, and a single obvious next step.
 *
 * Both callers pass the same two actions, because both need the same thing —
 * "where is my order" and "let me keep shopping" — even though the cart
 * route and the checkout route navigate away from different places.
 */
@Composable
fun OrderPlacedDialog(
    onTrackOrder: () -> Unit,
    onContinueShopping: () -> Unit
) {
    Dialog(
        onDismissRequest = onContinueShopping,
        // The order is already placed; a tap outside is just "ok, done",
        // which is exactly what Continue shopping does.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MyntraPinkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MyntraPink,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.order_placed_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.order_placed_body),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MyntraPink)
                        .dugguClickableFlat(onTrackOrder),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.order_placed_track),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dugguClickableFlat(onContinueShopping),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.order_placed_continue),
                        modifier = Modifier.padding(vertical = 10.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
