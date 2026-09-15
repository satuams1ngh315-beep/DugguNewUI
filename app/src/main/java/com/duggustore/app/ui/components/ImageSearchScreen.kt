package com.duggustore.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.duggustore.app.R
import com.duggustore.app.platform.ImageSearch
import com.duggustore.app.ui.theme.*

/** What the shot is doing right now, which is all the chrome needs to know. */
private sealed interface ShotState {
    data object Framing : ShotState
    data object Reading : ShotState
    data object NothingRecognised : ShotState
}

/**
 * Search by photo, on the app's own screen.
 *
 * Point at a product and press the shutter, or pick a photo already on the
 * phone. What comes back is the kind of thing in the picture, which goes
 * straight into the search field as if it had been typed.
 */
@Composable
fun ImageSearchScreen(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRefused by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ShotState>(ShotState.Framing) }

    // Held here so the shutter can reach it and it is dropped when this
    // screen leaves composition.
    val imageCapture = remember { ImageCapture.Builder().build() }
    // Capture callbacks on the main thread: they only copy the JPEG out of
    // the buffer and hand it to state, and state is read by composition.
    val captureExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    // A photo waiting to be read, as bytes plus the rotation the capture
    // reported — labelled in a coroutine rather than on the camera callback.
    var pending by remember { mutableStateOf<Pair<ByteArray, Int>?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionRefused = !granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            state = ShotState.Reading
            pendingUri = uri
        }
    }

    // Asked on the way into the screen rather than at launch, so nobody sees
    // a camera prompt for a feature they have not reached for.
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(pending) {
        val shot = pending ?: return@LaunchedEffect
        val label = ImageSearch.labelForPhoto(shot.first, shot.second)
        pending = null
        if (label != null) onResult(label) else state = ShotState.NothingRecognised
    }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        val label = ImageSearch.labelForUri(context, uri)
        pendingUri = null
        if (label != null) onResult(label) else state = ShotState.NothingRecognised
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasPermission) {
                CameraViewfinder(imageCapture = imageCapture)
            }

            ViewfinderFrame()

            ImageSearchChrome(
                state = state,
                canShoot = hasPermission && state != ShotState.Reading,
                onDismiss = onDismiss,
                onPickPhoto = { galleryLauncher.launch("image/*") },
                onShutter = {
                    state = ShotState.Reading
                    imageCapture.takePicture(
                        captureExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val rotation = image.imageInfo.rotationDegrees
                                image.close()
                                pending = bytes to rotation
                            }

                            override fun onError(exception: ImageCaptureException) {
                                state = ShotState.NothingRecognised
                            }
                        }
                    )
                },
                message = when {
                    permissionRefused -> stringResource(R.string.image_search_permission_needed)
                    !hasPermission -> stringResource(R.string.image_search_permission_waiting)
                    state == ShotState.Reading -> stringResource(R.string.image_search_reading)
                    state == ShotState.NothingRecognised ->
                        stringResource(R.string.image_search_no_match)
                    else -> stringResource(R.string.image_search_hint)
                }
            )
        }
    }
}

/** Live preview, with the still-capture use case bound alongside it. */
@Composable
private fun CameraViewfinder(imageCapture: ImageCapture) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    // No usable back camera, or another app holding it. The
                    // screen stays up with its message and the gallery route
                    // still works.
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

/** A dark scrim with a teal-edged window showing where to put the product. */
@Composable
private fun ViewfinderFrame() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val windowWidth = size.width * 0.76f
        val window = Rect(
            offset = Offset(
                x = (size.width - windowWidth) / 2f,
                y = (size.height - windowWidth) / 2f
            ),
            size = Size(windowWidth, windowWidth)
        )
        val radius = CornerRadius(26.dp.toPx(), 26.dp.toPx())

        // Scrim as one path with the window subtracted, rather than four
        // boxes around it — the corners stay properly rounded that way.
        val scrim = Path.combine(
            operation = PathOperation.Difference,
            path1 = Path().apply { addRect(Rect(Offset.Zero, size)) },
            path2 = Path().apply { addRoundRect(RoundRect(window, radius)) }
        )
        drawPath(scrim, Color.Black.copy(alpha = 0.58f))

        drawRoundRect(
            color = Teal,
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = radius,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** Close, title, hint, shutter and the gallery route — the app's own type. */
@Composable
private fun BoxScope.ImageSearchChrome(
    state: ShotState,
    canShoot: Boolean,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onShutter: () -> Unit,
    message: String
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .dugguClickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.dialog_close),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.home_image_search),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.92f)
        )

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .dugguClickable { onPickPhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.image_search_gallery),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Ring and fill rather than one disc, so the shutter still reads
            // as a shutter against a busy viewfinder.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    // Always attached, gated inside: a composable modifier
                    // appearing and disappearing from the chain as canShoot
                    // flips would take its remembered state with it.
                    .dugguClickable { if (canShoot) onShutter() },
                contentAlignment = Alignment.Center
            ) {
                if (state == ShotState.Reading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (canShoot) Teal else Color.White.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
