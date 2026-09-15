package com.duggustore.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duggustore.app.data.model.Product
import com.duggustore.app.ui.theme.*

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search products..."
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        placeholder = { Text(placeholder, color = TextLight) },
        leadingIcon = { Icon(Icons.Default.Search, "Search", tint = TextSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, "Clear", tint = TextSecondary)
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGreen,
            unfocusedBorderColor = BorderGray,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
fun ProductCard(
    product: Product,
    onAddClick: (Product) -> Unit,
    modifier: Modifier = Modifier,
    showDiscount: Boolean = true,
    onClick: ((Product) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick(product) } else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Product Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Background),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUrl != null) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.ShoppingBag,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = TextLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )

            if (product.description.isNotEmpty()) {
                Text(
                    text = product.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (product.hasDiscount() && showDiscount) {
                        Text(
                            text = "₹${product.price}",
                            fontSize = 12.sp,
                            color = TextLight,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                    Text(
                        text = "₹${product.effectivePrice()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (product.hasDiscount()) AccentOrange else TextPrimary
                    )
                }

                Button(
                    onClick = { onAddClick(product) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (product.stock > 0) PrimaryGreen else TextLight
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    enabled = product.stock > 0
                ) {
                    Text(
                        text = if (product.stock > 0) "Add" else "Out of Stock",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }

            if (product.hasDiscount()) {
                val savingPct = ((product.savingsAmount() / product.price) * 100).toInt()
                Text(
                    text = "Save $savingPct%",
                    fontSize = 10.sp,
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun CartItemRow(
    product: Product,
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Product image
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Background),
            contentAlignment = Alignment.Center
        ) {
            if (product.imageUrl != null) {
                AsyncImage(model = product.imageUrl, contentDescription = product.name)
            } else {
                Icon(Icons.Default.ShoppingBag, null, tint = TextLight, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = "₹${product.effectivePrice()}",
                fontSize = 13.sp,
                color = PrimaryGreen,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Quantity controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = if (quantity <= 1) onRemove else onDecrement,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (quantity <= 1) Icons.Default.Delete else Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = if (quantity <= 1) AccentRed else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = "$quantity",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, "Increase", tint = PrimaryGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = TextLight
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "pending" -> Pair(PendingYellow.copy(alpha = 0.15f), WarningYellow)
        "confirmed", "preparing" -> Pair(InfoBlue.copy(alpha = 0.15f), InfoBlue)
        "ready_for_pickup" -> Pair(WarningYellow.copy(alpha = 0.15f), WarningYellow)
        "out_for_delivery" -> Pair(PrimaryGreen.copy(alpha = 0.15f), PrimaryGreen)
        "delivered" -> Pair(DeliveredGreen.copy(alpha = 0.15f), DeliveredGreen)
        "cancelled" -> Pair(AccentRed.copy(alpha = 0.15f), AccentRed)
        else -> Pair(Background, TextSecondary)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = status.replace("_", " ").uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
fun ErrorRetryBlock(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = TextLight
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal)
        ) {
            Text("Retry", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionText,
                    color = PrimaryGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * The standard Indian veg/non-veg mark: a square outline holding a green
 * dot for veg or a brown/maroon triangle for non-veg. Callers decide
 * whether to show it at all — [Product.isVeg] is null for anything that
 * isn't food, and this composable always draws one or the other.
 */
@Composable
fun VegNonVegMark(isVeg: Boolean, modifier: Modifier = Modifier, boxSize: Dp = 14.dp) {
    val markColor = if (isVeg) VegGreen else NonVegBrown
    Box(
        modifier = modifier
            .size(boxSize)
            .border(BorderStroke(1.dp, markColor), RoundedCornerShape(2.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isVeg) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.55f)
                    .clip(CircleShape)
                    .background(markColor)
            )
        } else {
            // A same-named parameter on the enclosing composable (boxSize,
            // renamed from `size` for exactly this reason) would otherwise
            // shadow DrawScope's own `size: Size` here — using it unqualified
            // reads fine but resolves to the wrong thing.
            Canvas(modifier = Modifier.fillMaxSize(0.75f)) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = markColor)
            }
        }
    }
}

@Composable
private fun ProductCardSkeleton(brush: Brush, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.6f))
    ) {
        Column {
            SkeletonBlock(
                brush = brush,
                modifier = Modifier.fillMaxWidth().height(130.dp),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.8f).height(14.dp))
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(brush, Modifier.fillMaxWidth(0.4f).height(16.dp))
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(brush, Modifier.fillMaxWidth().height(32.dp))
            }
        }
    }
}

@Composable
private fun SkeletonBlock(brush: Brush, modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(6.dp)) {
    Box(modifier = modifier.clip(shape).background(brush))
}
