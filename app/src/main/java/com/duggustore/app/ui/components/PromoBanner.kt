package com.duggustore.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.duggustore.app.data.model.Product

/**
 * One slot in the home offer rail. Not every slot advertises a coupon any
 * more, so this carries only what every kind of card needs to render itself
 * — a tint, an icon+label, a headline, an optional chip/corner-tag/photo —
 * plus whatever a tap on it should do.
 */
data class PromoBanner(
    val id: String,
    val tint: Color,
    val eyebrowIcon: ImageVector,
    val eyebrow: String,
    val headline: String,
    val subtitle: String,
    val chipLabel: String? = null,
    val cornerTag: String? = null,
    val featuredProduct: Product? = null,
    val onClick: () -> Unit
)
