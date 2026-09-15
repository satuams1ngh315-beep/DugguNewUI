package com.duggustore.app.ui.screens.seller

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.duggustore.app.data.model.Category
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.repository.ProductRepository
import com.duggustore.app.data.remote.BackgroundRemovalService
import com.duggustore.app.ui.components.AuthField
import com.duggustore.app.ui.components.StoreProductCard
import com.duggustore.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AddEditProductScreen(
    product: Product?,
    categories: List<Category>,
    sellerId: String,
    isLoading: Boolean,
    error: String? = null,
    /**
     * Pre-selects the category for a brand-new product — what "quick sell"
     * hands over. Ignored while editing, where the product's own category is
     * the only thing that could be right.
     */
    initialCategoryId: String = "",
    onSave: (Product) -> Unit,
    onBack: () -> Unit
) {
    val editing = product != null

    var name by remember(product) { mutableStateOf(product?.name ?: "") }
    var description by remember(product) { mutableStateOf(product?.description ?: "") }
    var price by remember(product) { mutableStateOf(product?.price?.takeIf { it > 0 }?.toString() ?: "") }
    var discountPrice by remember(product) { mutableStateOf(product?.discountPrice?.toString() ?: "") }
    var stock by remember(product) { mutableStateOf(product?.stock?.toString() ?: "") }
    var unit by remember(product) { mutableStateOf(product?.unit ?: "pcs") }
    var photos by remember(product) { mutableStateOf(product?.images() ?: emptyList()) }
    var categoryId by remember(product, initialCategoryId) {
        mutableStateOf(product?.categoryId?.takeIf { it.isNotBlank() } ?: initialCategoryId)
    }
    var isActive by remember(product) { mutableStateOf(product?.isActive ?: true) }
    // Null ("not food") is the default — most grocery items don't need this
    // mark at all, so nothing is pre-selected for a brand-new product.
    var isVeg by remember(product) { mutableStateOf(product?.isVeg) }
    var localError by remember { mutableStateOf<String?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }
    var showSourceMenu by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val priceValue = price.toDoubleOrNull()
    val discountValue = discountPrice.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val shownError = localError ?: error

    // Same shape the save button builds, kept live so the card below reflects
    // every field as the seller edits it rather than only the saved photo.
    val previewProduct = Product(
        id = product?.id ?: "",
        sellerId = product?.sellerId?.takeIf { it.isNotBlank() } ?: sellerId,
        categoryId = categoryId,
        name = name.trim().ifBlank { "Product name" },
        description = description.trim(),
        price = priceValue ?: 0.0,
        discountPrice = discountValue,
        imageUrl = photos.firstOrNull(),
        imageUrls = photos,
        stock = stock.toIntOrNull() ?: 0,
        unit = unit.trim().ifBlank { "pcs" },
        isActive = isActive,
        isVeg = isVeg
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val productRepo = remember { ProductRepository() }

    // Trigger server-side background removal for the given image.
    // Declared before processAndUpload: Kotlin local functions
    // cannot be called before their declaration.
    suspend fun triggerBackgroundRemoval(imageUrl: String, productId: String) {
        if (!BackgroundRemovalService.isEnabled() || productId.isBlank()) return
        try {
            // Call the server-side API to process the image
            // This is non-blocking - the upload completes immediately
            // and the background removal happens asynchronously
            BackgroundRemovalService.processImage(imageUrl, productId)
        } catch (e: Exception) {
            // Don't block the upload if background removal fails
            // The image will still work without the cutout
            // Log the error for debugging
            e.printStackTrace()
        }
    }

    // Shared by both the gallery picker and the camera: read the bytes,
    // upload the original image, and trigger server-side background removal.
    // No on-device ML Kit processing - much better performance.
    suspend fun processAndUpload(uri: Uri): String? {
        val read = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read the selected image")
                bytes to mimeType
            }
        }
        val (bytes, mimeType) = read.getOrNull() ?: return null

        // Upload original image - server will handle background removal
        val imageUrl = productRepo.uploadProductImage(sellerId, bytes, mimeType).getOrNull() ?: return null
        
        // Trigger server-side background removal for the uploaded image
        triggerBackgroundRemoval(imageUrl, previewProduct.id)
        
        return imageUrl
    }

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PRODUCT_PHOTOS)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uploadingImage = true
        scope.launch {
            val uploaded = mutableListOf<String>()
            var failed = false
            for (uri in uris) {
                if (photos.size + uploaded.size >= MAX_PRODUCT_PHOTOS) break
                val url = processAndUpload(uri)
                if (url != null) uploaded.add(url) else failed = true
            }
            photos = (photos + uploaded).take(MAX_PRODUCT_PHOTOS)
            localError = if (failed) "Some photos couldn't be uploaded — try those again" else null
            uploadingImage = false
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (!success || uri == null) return@rememberLauncherForActivityResult
        uploadingImage = true
        scope.launch {
            val url = processAndUpload(uri)
            if (url != null) {
                photos = (photos + url).take(MAX_PRODUCT_PHOTOS)
                localError = null
            } else {
                localError = "Couldn't upload that photo — try again"
            }
            uploadingImage = false
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = newCameraPhotoUri(context)
            pendingCameraUri = uri
            takePicture.launch(uri)
        } else {
            localError = "Camera permission is needed to take a photo"
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = newCameraPhotoUri(context)
            pendingCameraUri = uri
            takePicture.launch(uri)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        Surface(color = Teal) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    text = if (editing) "Edit product" else "Add product",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Customer preview",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StoreProductCard(
                    product = previewProduct,
                    quantityInCart = 0,
                    isFavorite = false,
                    onAdd = {},
                    onIncrease = {},
                    onDecrease = {},
                    onToggleFavorite = {},
                    onClick = {},
                    modifier = Modifier.width(180.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            ProductPhotosPicker(
                photos = photos,
                uploading = uploadingImage,
                showSourceMenu = showSourceMenu,
                onOpenSourceMenu = { showSourceMenu = true },
                onDismissSourceMenu = { showSourceMenu = false },
                onTakePhoto = {
                    showSourceMenu = false
                    launchCamera()
                },
                onPickGallery = {
                    showSourceMenu = false
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onAddUrl = {
                    showSourceMenu = false
                    showUrlDialog = true
                },
                onRemovePhoto = { url -> photos = photos.filterNot { it == url } }
            )

            if (showUrlDialog) {
                AddPhotoUrlDialog(
                    onAdd = { url ->
                        photos = (photos + url).take(MAX_PRODUCT_PHOTOS)
                        // Trigger server-side background removal for URL images too
                        scope.launch {
                            triggerBackgroundRemoval(url, previewProduct.id)
                        }
                        showUrlDialog = false
                    },
                    onDismiss = { showUrlDialog = false }
                )
            }

            Spacer(Modifier.height(16.dp))

            AuthField(
                value = name,
                onValueChange = { name = it; localError = null },
                label = "Product name",
                placeholder = "What are you selling?"
            )

            Spacer(Modifier.height(16.dp))

            AuthField(
                value = description,
                onValueChange = { description = it },
                label = "Description",
                placeholder = "Optional"
            )

            Spacer(Modifier.height(16.dp))

            CategoryDropdown(
                categories = categories,
                selected = selectedCategory,
                onSelect = { categoryId = it.id; localError = null }
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AuthField(
                        value = price,
                        onValueChange = { price = it; localError = null },
                        label = "Price (₹)",
                        placeholder = "0",
                        keyboardType = KeyboardType.Decimal
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AuthField(
                        value = discountPrice,
                        onValueChange = { discountPrice = it; localError = null },
                        label = "Sale price",
                        placeholder = "Optional",
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    AuthField(
                        value = stock,
                        onValueChange = { stock = it; localError = null },
                        label = "Stock",
                        placeholder = "0",
                        keyboardType = KeyboardType.Number
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AuthField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = "Unit",
                        placeholder = "kg, pcs…"
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Veg / non-veg",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VegChoiceChip(
                    label = "Not food",
                    selected = isVeg == null,
                    onClick = { isVeg = null },
                    modifier = Modifier.weight(1f)
                )
                VegChoiceChip(
                    label = "Veg",
                    selected = isVeg == true,
                    onClick = { isVeg = true },
                    dotColor = VegGreen,
                    modifier = Modifier.weight(1f)
                )
                VegChoiceChip(
                    label = "Non-veg",
                    selected = isVeg == false,
                    onClick = { isVeg = false },
                    dotColor = NonVegBrown,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Visible to customers",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isActive) "Listed in the store" else "Hidden from the store",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Teal
                        )
                    )
                }
            }

            if (shownError != null) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = CoralSurface
                ) {
                    Text(
                        text = shownError,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        color = CoralDark,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        Surface(
            color = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            shadowElevation = 18.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        localError = when {
                            name.isBlank() -> "Enter a product name"
                            categoryId.isBlank() -> "Pick a category"
                            priceValue == null || priceValue <= 0 -> "Enter a valid price"
                            // The products table enforces discount_price < price, so a bad
                            // value would come back as an opaque database error.
                            discountValue != null && discountValue >= priceValue ->
                                "Sale price must be lower than the price"
                            stock.toIntOrNull() == null || stock.toInt() < 0 -> "Enter a valid stock count"
                            else -> null
                        }
                        if (localError == null) {
                            onSave(
                                Product(
                                    id = product?.id ?: "",
                                    sellerId = product?.sellerId?.takeIf { it.isNotBlank() } ?: sellerId,
                                    categoryId = categoryId,
                                    name = name.trim(),
                                    description = description.trim(),
                                    price = priceValue ?: 0.0,
                                    discountPrice = discountValue,
                                    imageUrl = photos.firstOrNull(),
                                    imageUrls = photos,
                                    stock = stock.toIntOrNull() ?: 0,
                                    unit = unit.trim().ifBlank { "pcs" },
                                    isActive = isActive,
                                    isVeg = isVeg
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal,
                        disabledContainerColor = BorderGray
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (editing) "Save changes" else "Add product",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/** Up to this many photos per product — enough to show a product from a few angles without turning the form into a gallery manager. */
private const val MAX_PRODUCT_PHOTOS = 6

/**
 * A row of the product's photos — each removable, the first one marked as
 * the cover since it's what every screen that only knows a single image
 * (dashboards, order rows) falls back to — plus a tile for picking more
 * straight off the seller's device.
 */
@Composable
private fun ProductPhotosPicker(
    photos: List<String>,
    uploading: Boolean,
    showSourceMenu: Boolean,
    onOpenSourceMenu: () -> Unit,
    onDismissSourceMenu: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onAddUrl: () -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    Column {
        Text("Photos", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(photos, key = { it }) { url ->
                Box(modifier = Modifier.size(96.dp)) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Product photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceMuted)
                    )
                    if (url == photos.firstOrNull()) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Teal
                        ) {
                            Text(
                                "Cover",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { onRemovePhoto(url) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }

            if (photos.size < MAX_PRODUCT_PHOTOS) {
                item {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceMuted)
                                .border(1.dp, BorderGray, RoundedCornerShape(14.dp))
                                .clickable(enabled = !uploading) { onOpenSourceMenu() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uploading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Teal, strokeWidth = 2.dp)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        if (photos.isEmpty()) Icons.Default.Image else Icons.Default.Add,
                                        contentDescription = null,
                                        tint = TextLight,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (photos.isEmpty()) "Add photos" else "Add more",
                                        fontSize = 11.sp,
                                        color = TextLight
                                    )
                                }
                            }
                        }

                        DropdownMenu(expanded = showSourceMenu, onDismissRequest = onDismissSourceMenu) {
                            DropdownMenuItem(
                                text = { Text("Take a photo") },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, null, tint = Teal) },
                                onClick = onTakePhoto
                            )
                            DropdownMenuItem(
                                text = { Text("Choose from gallery") },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, tint = Teal) },
                                onClick = onPickGallery
                            )
                            DropdownMenuItem(
                                text = { Text("Add via URL") },
                                leadingIcon = { Icon(Icons.Default.Link, null, tint = Teal) },
                                onClick = onAddUrl
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Prompts for a plain image link rather than a device photo — no upload, no background removal, just the URL as given. */
@Composable
private fun AddPhotoUrlDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a photo link") },
        text = {
            AuthField(
                value = url,
                onValueChange = { url = it },
                label = "Image URL",
                placeholder = "https://…"
            )
        },
        confirmButton = {
            TextButton(
                onClick = { url.trim().takeIf { it.isNotBlank() }?.let(onAdd) },
                enabled = url.isNotBlank()
            ) {
                Text("Add", color = Teal, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

/** A fresh, private, FileProvider-backed destination for the camera to write a full-resolution capture to. */
private fun newCameraPhotoUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "product_photos").apply { mkdirs() }
    val file = File(dir, "product_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selected: Category?,
    onSelect: (Category) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text("Category", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = SurfaceMuted,
            border = if (selected == null) BorderStroke(1.dp, BorderGray) else null
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected?.name ?: "Select a category",
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    color = if (selected == null) TextLight else TextPrimary
                )
                Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (categories.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No categories available", color = TextSecondary) },
                    onClick = { expanded = false }
                )
            } else {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = category.name,
                                fontWeight = if (category.id == selected?.id) FontWeight.Bold
                                             else FontWeight.Normal,
                                color = if (category.id == selected?.id) Teal else TextPrimary
                            )
                        },
                        onClick = { onSelect(category); expanded = false }
                    )
                }
            }
        }
    }
}

/** One of the three veg/non-veg options — a plain pill when unselected, filled once picked. */
@Composable
private fun VegChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) TealSurface else SurfaceWhite,
        border = BorderStroke(1.dp, if (selected) Teal else BorderGray)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) TealDark else TextSecondary
            )
        }
    }
}
