package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.ui.theme.*

/**
 * Myntra-style sub-screen header: white bar over a hairline divider, an
 * ink-grey back chevron, bold ink title and subtle grey subtitle. Action
 * icons sit on the right tinted ink, with the screen itself scrolling under
 * a plain white band.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Surface(
        color = Color.Transparent,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (onBack != null) Dimens.SpaceSm else Dimens.ScreenPadding,
                        end = Dimens.SpaceSm,
                        top = Dimens.SpaceSm,
                        bottom = Dimens.SpaceSm
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.IconButtonSize)
                            .clip(CircleShape)
                            .dugguClickable(onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(Dimens.IconMd)
                        )
                    }
                    Spacer(Modifier.width(Dimens.SpaceXs))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                actions()
            }

            if (content != null) {
                content()
                Spacer(Modifier.height(Dimens.ScreenPadding))
            }

            // Hairline divider under the white bar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(DividerGray)
            )
        }
    }
}

@Composable
fun ScreenHeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(Dimens.IconButtonSize)
            .clip(CircleShape)
            .background(SurfaceMuted.copy(alpha = 0.6f))
            .dugguClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(Dimens.IconMd)
        )
    }
}
