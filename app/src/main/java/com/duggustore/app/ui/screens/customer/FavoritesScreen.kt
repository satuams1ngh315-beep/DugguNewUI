package com.duggustore.app.ui.screens.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.data.model.Product
import com.duggustore.app.ui.components.ProductGridSkeleton
import com.duggustore.app.ui.components.ScreenHeader
import com.duggustore.app.ui.components.StoreProductCard
import com.duggustore.app.ui.theme.*

@Composable
fun FavoritesScreen(
    favoriteProducts: List<Product>,
    onAddToCart: (Product) -> Unit,
    onBack: () -> Unit,
    onProductClick: (Product) -> Unit = {},
    cartQuantities: Map<String, Int> = emptyMap(),
    onIncrease: (Product) -> Unit = {},
    onDecrease: (Product) -> Unit = {},
    onRemoveFavorite: (Product) -> Unit = {},
    isLoading: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        ScreenHeader(
            title = stringResource(R.string.favourites_title),
            // Left off entirely while loading rather than announcing
            // "0 products" and correcting itself a moment later.
            subtitle = when {
                isLoading -> null
                favoriteProducts.size == 1 -> stringResource(R.string.favourites_one)
                else -> stringResource(R.string.favourites_count, favoriteProducts.size)
            },
            onBack = onBack
        )

        if (isLoading) {
            // The grid this is standing in for, rather than a spinner in the
            // middle of an empty page: the first frame already has the shape
            // of the answer, so the content fills in rather than replacing a
            // blank screen.
            ProductGridSkeleton()
        } else if (favoriteProducts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(MyntraPinkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = MyntraPink,
                        modifier = Modifier.size(46.dp)
                    )
                }
                Spacer(Modifier.height(Dimens.SpaceLg))
                Text(
                    stringResource(R.string.favourites_empty_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(Dimens.SpaceSm))
                Text(
                    text = stringResource(R.string.favourites_empty_subtitle),
                    modifier = Modifier.padding(horizontal = 40.dp),
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd)
            ) {
                items(favoriteProducts, key = { it.id }) { product ->
                    StoreProductCard(
                        product = product,
                        quantityInCart = cartQuantities[product.id] ?: 0,
                        // Everything on this screen is a favourite by
                        // definition, so the heart is always filled and
                        // tapping it removes the product.
                        isFavorite = true,
                        onAdd = { onAddToCart(product) },
                        onIncrease = { onIncrease(product) },
                        onDecrease = { onDecrease(product) },
                        onToggleFavorite = { onRemoveFavorite(product) },
                        onClick = { onProductClick(product) }
                    )
                }
            }
        }
    }
}
