package com.duggustore.app.ui.screens.customer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duggustore.app.BuildConfig
import com.duggustore.app.data.model.UserProfile
import com.duggustore.app.ui.components.ScreenHeader
import com.duggustore.app.ui.components.dugguClickable
import com.duggustore.app.ui.theme.*

@Composable
fun SettingsScreen(
    user: UserProfile?,
    isSaving: Boolean,
    error: String?,
    passwordUpdated: Boolean,
    onSaveProfile: (fullName: String, phone: String) -> Unit,
    onChangePassword: (newPassword: String, confirmPassword: String) -> Unit,
    onUploadAvatar: (bytes: ByteArray, mimeType: String) -> Unit,
    onClearError: () -> Unit,
    onClearPasswordUpdated: () -> Unit,
    onBack: () -> Unit
) {
    var showEditProfile by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        ScreenHeader(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.Person,
                    title = "Edit profile",
                    subtitle = user?.fullName?.takeIf { it.isNotBlank() } ?: "Name and phone number",
                    onClick = { showEditProfile = true }
                )
                Divider(color = BorderGray, modifier = Modifier.padding(start = 68.dp))
                SettingsRow(
                    icon = Icons.Default.Lock,
                    title = "Change password",
                    subtitle = "Update your login password",
                    tint = Orange,
                    onClick = { showChangePassword = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup {
                SettingsRow(
                    icon = Icons.Default.Policy,
                    title = "Terms & Privacy",
                    subtitle = "How your data is used",
                    tint = InfoBlue,
                    onClick = { showTerms = true }
                )
                Divider(color = BorderGray, modifier = Modifier.padding(start = 68.dp))
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "About Duggu Store",
                    subtitle = "Version ${BuildConfig.VERSION_NAME}",
                    tint = TextSecondary,
                    onClick = { showAbout = true }
                )
            }
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            user = user,
            isSaving = isSaving,
            error = error,
            onDismiss = { showEditProfile = false; onClearError() },
            onSave = { name, phone -> onSaveProfile(name, phone) },
            onUploadAvatar = onUploadAvatar,
            closeOnSaved = { showEditProfile = false }
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            isSaving = isSaving,
            error = error,
            passwordUpdated = passwordUpdated,
            onDismiss = {
                showChangePassword = false
                onClearError()
                onClearPasswordUpdated()
            },
            onSave = { newPassword, confirm -> onChangePassword(newPassword, confirm) }
        )
    }

    if (showAbout) {
        InfoDialog(
            title = "About Duggu Store",
            body = "Duggu Store is a quick-commerce app connecting local sellers, riders and customers — groceries and essentials delivered fast.\n\nVersion ${BuildConfig.VERSION_NAME}",
            onDismiss = { showAbout = false }
        )
    }

    if (showTerms) {
        InfoDialog(
            title = "Terms & Privacy",
            body = "By using Duggu Store you agree to order responsibly and provide accurate delivery details. " +
                "Sellers and riders are verified before they can operate on the platform.\n\n" +
                "We store your name, phone number, addresses and order history to fulfil your orders, and your " +
                "location only while placing an order or, for sellers and riders, to route pickups and deliveries. " +
                "This data is never sold to third parties.",
            onDismiss = { showTerms = false }
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Dimens.ShapeMd,
        color = SurfaceWhite,
        shadowElevation = 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: Color = BlinkitGreen
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dugguClickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.12f), Dimens.ShapeSm),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextLight)
    }
}

@Composable
private fun EditProfileDialog(
    user: UserProfile?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (fullName: String, phone: String) -> Unit,
    onUploadAvatar: (bytes: ByteArray, mimeType: String) -> Unit,
    closeOnSaved: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(user?.fullName.orEmpty()) }
    var phone by remember { mutableStateOf(user?.phone.orEmpty()) }
    var wasSaving by remember { mutableStateOf(false) }

    // Closes itself once the save that was in flight finishes without error,
    // rather than the caller having to track dialog visibility from outside.
    LaunchedEffect(isSaving, error) {
        if (wasSaving && !isSaving && error == null) closeOnSaved()
        wasSaving = isSaving
    }

    // Single-image picker — opens the system photo picker (Android 13+) or
    // falls back to the legacy chooser on older devices automatically.
    val pickAvatar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        onUploadAvatar(bytes, mimeType)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (error != null) {
                    Text(error, color = CoralDark, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                }

                // Avatar preview + change button
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .size(80.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    val avatarUrl = user?.avatarUrl
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(2.dp, BorderGray, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(TealSurface)
                                .border(2.dp, BorderGray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = Teal, modifier = Modifier.size(40.dp))
                        }
                    }
                    // Camera badge overlaid at bottom-right of the avatar circle
                    Surface(
                        modifier = Modifier
                            .size(26.dp)
                            .dugguClickable {
                                pickAvatar.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        shape = CircleShape,
                        color = BlinkitGreen,
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Change photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phone.isNotBlank() && !isSaving,
                onClick = { onSave(name.trim(), phone.trim()) }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BlinkitGreen)
                } else {
                    Text("Save", fontWeight = FontWeight.Bold, color = BlinkitGreen)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun ChangePasswordDialog(
    isSaving: Boolean,
    error: String?,
    passwordUpdated: Boolean,
    onDismiss: () -> Unit,
    onSave: (newPassword: String, confirm: String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (passwordUpdated) "Password updated" else "Change password") },
        text = {
            if (passwordUpdated) {
                Text("Your password has been changed.", fontSize = 14.sp, color = TextPrimary)
            } else {
                Column {
                    if (error != null) {
                        Text(error, color = CoralDark, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm new password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (passwordUpdated) {
                TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.Bold, color = BlinkitGreen) }
            } else {
                TextButton(
                    enabled = newPassword.isNotBlank() && confirm.isNotBlank() && !isSaving,
                    onClick = { onSave(newPassword, confirm) }
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BlinkitGreen)
                    } else {
                        Text("Save", fontWeight = FontWeight.Bold, color = BlinkitGreen)
                    }
                }
            }
        },
        dismissButton = {
            if (!passwordUpdated) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                body,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 19.sp,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", fontWeight = FontWeight.Bold, color = BlinkitGreen) }
        }
    )
}
