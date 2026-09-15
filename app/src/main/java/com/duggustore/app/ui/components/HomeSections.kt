package com.duggustore.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duggustore.app.data.model.HomeSection
import com.duggustore.app.data.model.HomeSectionCategory
import com.duggustore.app.ui.theme.*

/**
 * One block of the home page's browse layout.
 *
 * The admin picks the title, the layout and the categories; everything shown
 * inside comes from the sellers' live products in those categories, so a
 * section starts working the moment stock exists rather than waiting on
 * someone to upload artwork for it.
 */
@Composable
fun HomeSectionBlock(
    section: HomeSection,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (section.categories.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = section.title,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(14.dp))

        when (section.layout) {
            HomeSection.LAYOUT_COLLAGE -> CollageRow(section.categories, onCategoryClick)
            else -> TileGrid(section.categories, onCategoryClick)
        }
    }
}

/**
 * The four-photo card. Scrolls sideways rather than wrapping: these cards are
 * wide, and two per screen with more clearly off the edge invites a swipe,
 * where a two-column wrap would just look like a short list.
 */
@Composable
private fun CollageRow(
    categories: List<HomeSectionCategory>,
    onCategoryClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            CollageCard(category = category, onClick = { onCategoryClick(category.id) })
        }
    }
}

@Composable
private fun CollageCard(category: HomeSectionCategory, onClick: () -> Unit) {
    val photos = category.previewImages.filter { it.isNotBlank() }.take(4)
    Surface(
        modifier = Modifier.width(190.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceMuted
    ) {
        Column(
            modifier = Modifier
                .dugguClickable(onClick)
                .padding(10.dp)
        ) {
            Box {
                CollagePhotos(photos)

                // Only once there is more behind the four on show — on a
                // category with three products it would be a lie.
                if (category.productCount > photos.size && photos.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 9.dp),
                        shape = RoundedCornerShape(50),
                        color = SurfaceWhite
                    ) {
                        Text(
                            text = "+${category.productCount - photos.size} more",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = category.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Fills the card with the photos that actually exist. A forced 2×2 used to
 * draw empty Image-icon cells for every missing slot — two products looked
 * like a broken grid rather than a collage.
 */
@Composable
private fun CollagePhotos(photos: List<String>) {
    when (photos.size) {
        0 -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceWhite),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Image, contentDescription = null, tint = BorderGray, modifier = Modifier.size(28.dp))
        }
        1 -> PhotoCell(photos[0], Modifier.fillMaxWidth())
        2 -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            photos.forEach { PhotoCell(it, Modifier.weight(1f)) }
        }
        3 -> Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PhotoCell(photos[0], Modifier.weight(1f).fillMaxHeight(), square = false)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PhotoCell(photos[1], Modifier.weight(1f).fillMaxWidth(), square = false)
                    PhotoCell(photos[2], Modifier.weight(1f).fillMaxWidth(), square = false)
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) { column ->
                        PhotoCell(photos[row * 2 + column], Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(url: String, modifier: Modifier = Modifier, square: Boolean = true) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .then(if (square) Modifier.aspectRatio(1f) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceWhite),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = remember(url) { feedImageRequest(context, url, sizePx = 256) },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * The plain grid: one photo per category, four across, wrapping onto as many
 * rows as it takes. Laid out as Rows rather than a LazyVerticalGrid because
 * this sits inside the home LazyColumn, and a lazy grid nested in a lazy
 * column has no height to measure against.
 */
@Composable
private fun TileGrid(
    categories: List<HomeSectionCategory>,
    onCategoryClick: (String) -> Unit
) {
    val columns = 4
    // Chunked once per category list rather than on every recomposition of
    // the page around it.
    val rows = remember(categories) { categories.chunked(columns) }
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { category ->
                    CategoryTileCard(
                        category = category,
                        onClick = { onCategoryClick(category.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keeps a short last row's tiles the same width as a full
                // one's instead of stretching them across the screen.
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTileCard(
    category: HomeSectionCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.dugguClickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            val url = category.previewImages.firstOrNull()
            if (url != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = remember(url) { feedImageRequest(context, url, sizePx = 256) },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    iconForCategory(category.name),
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = category.name,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
