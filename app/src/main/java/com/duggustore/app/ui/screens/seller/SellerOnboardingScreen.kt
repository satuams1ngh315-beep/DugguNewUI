package com.duggustore.app.ui.screens.seller

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duggustore.app.R
import com.duggustore.app.data.model.SELLER_DOC_TYPES
import com.duggustore.app.data.model.Seller
import com.duggustore.app.data.model.SellerDocument
import com.duggustore.app.data.model.docTypeLabel
import com.duggustore.app.ui.components.DocumentUploadRow
import com.duggustore.app.ui.components.LocationPickerField
import com.duggustore.app.ui.components.StepHeading
import com.duggustore.app.ui.components.StepIllustration
import com.duggustore.app.ui.components.StepProgressBar
import com.duggustore.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STEP_BUSINESS = 0
private const val STEP_PHONE = 1
private const val STEP_ADDRESS = 2
private const val STEP_TAX = 3
private const val STEP_BANK = 4
private const val STEP_DOCUMENTS = 5
private const val STEP_COUNT = 6

/**
 * A seller's application, one question per screen — same pattern as sign-up:
 * a progress bar, a "Step X of Y" count, and a back arrow that steps
 * backward rather than a single long form dumping every field at once.
 * Business + bank details, PAN/GST/FSSAI, and the documents that back them
 * up, matching what a real quick-commerce marketplace asks for.
 *
 * The application row has to exist before a document can be attached to it
 * (seller_documents' foreign key requires it), so moving on from the bank
 * details step saves everything collected so far — the documents step then
 * unlocks once that save has landed, same as it always has.
 */
@Composable
fun SellerOnboardingScreen(
    userId: String,
    prefillEmail: String,
    prefillPhone: String,
    existing: Seller?,
    documents: List<SellerDocument>,
    isSaving: Boolean,
    isSubmitting: Boolean,
    uploadingDocType: String?,
    error: String?,
    onUploadDocument: (docType: String, bytes: ByteArray, contentType: String) -> Unit,
    onSave: (Seller) -> Unit,
    onSubmit: () -> Unit,
    onClearError: () -> Unit,
    onLocationPicked: (address: String, lat: Double, lng: Double) -> Unit,
    onSignOut: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(STEP_BUSINESS) }
    var movingForward by remember { mutableStateOf(true) }

    var businessName by remember(existing) { mutableStateOf(existing?.businessName.orEmpty()) }
    var ownerName by remember(existing) { mutableStateOf(existing?.ownerName.orEmpty()) }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: prefillPhone) }
    var pan by remember(existing) { mutableStateOf(existing?.panNumber.orEmpty()) }
    var gst by remember(existing) { mutableStateOf(existing?.gstNumber.orEmpty()) }
    var fssai by remember(existing) { mutableStateOf(existing?.fssaiNumber.orEmpty()) }
    var bankAccount by remember(existing) { mutableStateOf(existing?.bankAccountNumber.orEmpty()) }
    var bankIfsc by remember(existing) { mutableStateOf(existing?.bankIfsc.orEmpty()) }
    var upiId by remember(existing) { mutableStateOf(existing?.upiId.orEmpty()) }
    var businessAddress by remember(existing) { mutableStateOf(existing?.businessAddress.orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }
    var pendingDocType by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val docType = pendingDocType
        pendingDocType = null
        if (uri == null || docType == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val mimeType = resolver.getType(uri) ?: "image/jpeg"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Could not read the selected file")
                    bytes to mimeType
                }
            }.getOrNull()
            if (read != null) onUploadDocument(docType, read.first, read.second)
        }
    }

    val documentsByType = remember(documents) { documents.associateBy { it.docType } }
    val allDocsUploaded = remember(documents) { SELLER_DOC_TYPES.all { documentsByType.containsKey(it) } }

    // What the current step needs before Next means anything — mirrors
    // sign-up's stepProblem, so the button disables itself and explains why
    // instead of silently doing nothing.
    val stepProblem: String? = when (step) {
        STEP_BUSINESS -> when {
            businessName.isBlank() -> "Enter your business or store name"
            ownerName.isBlank() -> "Enter the owner's full name"
            else -> null
        }
        STEP_PHONE -> if (phone.isBlank()) "Enter a phone number" else null
        STEP_ADDRESS -> if (businessAddress.isBlank()) "Add your business or pickup address" else null
        STEP_TAX -> if (pan.isBlank()) "Enter your PAN number" else null
        STEP_BANK -> when {
            bankAccount.isBlank() -> "Enter your bank account number"
            bankIfsc.isBlank() -> "Enter your bank IFSC code"
            else -> null
        }
        else -> null
    }

    val shownError = localError ?: error

    fun currentSeller() = Seller(
        id = userId,
        businessName = businessName.trim(),
        ownerName = ownerName.trim(),
        email = prefillEmail,
        phone = phone.trim(),
        panNumber = pan.trim(),
        gstNumber = gst.trim().ifBlank { null },
        fssaiNumber = fssai.trim().ifBlank { null },
        bankAccountNumber = bankAccount.trim(),
        bankIfsc = bankIfsc.trim(),
        upiId = upiId.trim().ifBlank { null },
        businessAddress = businessAddress.trim()
    )

    fun goNext() {
        if (stepProblem != null) {
            localError = stepProblem
            return
        }
        localError = null
        onClearError()
        // The application row has to exist before a document can reference
        // it, so this is the one point that actually saves — the documents
        // step below unlocks once it lands.
        if (step == STEP_BANK) onSave(currentSeller())
        if (step < STEP_COUNT - 1) {
            movingForward = true
            step++
        }
    }

    fun goBack() {
        localError = null
        onClearError()
        if (step > STEP_BUSINESS) {
            movingForward = false
            step--
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
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Become a seller", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Fill this in once — an admin reviews it before you can list products",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onSignOut) {
                    Icon(Icons.Default.Logout, "Sign out", tint = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > STEP_BUSINESS) {
                IconButton(onClick = ::goBack) {
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.auth_back), tint = TextPrimary)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            StepProgressBar(current = step, total = STEP_COUNT, modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auth_step_of, step + 1, STEP_COUNT),
                modifier = Modifier.padding(horizontal = 12.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (existing?.rejectionReason?.isNotBlank() == true) {
                OnboardingErrorBanner("Your last application was rejected: ${existing.rejectionReason}")
                Spacer(Modifier.height(16.dp))
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = slideStepTransition(movingForward),
                label = "seller-onboarding-step"
            ) { s ->
                Column {
                    when (s) {
                        STEP_BUSINESS -> {
                            StepHeading("Tell us about your business", "This is what customers and riders will see.")
                            OnboardingField(businessName, { businessName = it }, "Business / store name")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(ownerName, { ownerName = it }, "Owner's full name")
                            StepIllustration(Icons.Default.Storefront, Orange, "Your store's public name — customers see this on their order.")
                        }
                        STEP_PHONE -> {
                            StepHeading("Your phone number", "A customer or rider calls this if there's a problem with an order.")
                            OnboardingField(phone, { phone = it }, "Phone number", keyboardType = KeyboardType.Phone)
                            StepIllustration(Icons.Default.Phone, Coral, "Kept private — only used for order-related calls.")
                        }
                        STEP_ADDRESS -> {
                            StepHeading("Where should riders pick up?", "Drag the map to place the pin exactly on your store.")
                            LocationPickerField(
                                address = businessAddress,
                                onAddressChange = { businessAddress = it },
                                onLocationPicked = { pickedAddress, lat, lng ->
                                    businessAddress = pickedAddress
                                    onLocationPicked(pickedAddress, lat, lng)
                                },
                                label = "Business / pickup address"
                            )
                        }
                        STEP_TAX -> {
                            StepHeading("Tax details", "PAN is required; GST and FSSAI only if you already have them.")
                            OnboardingField(pan, { pan = it.uppercase() }, "PAN number")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(gst, { gst = it.uppercase() }, "GST number (if you have one)")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(fssai, { fssai = it }, "FSSAI licence (for food & grocery)")
                            StepIllustration(Icons.Default.Receipt, Violet, "Needed for the invoices and tax filing on your orders.")
                        }
                        STEP_BANK -> {
                            StepHeading("Where should we pay you?", "Your order payouts go to this account.")
                            OnboardingField(bankAccount, { bankAccount = it }, "Bank account number", keyboardType = KeyboardType.Number)
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(bankIfsc, { bankIfsc = it.uppercase() }, "Bank IFSC code")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(upiId, { upiId = it }, "UPI ID (optional)")
                            StepIllustration(Icons.Default.AccountBalance, Teal, "Double-check these — payouts fail silently if they're wrong.")
                        }
                        else -> {
                            StepHeading("Upload your documents", "A clear photo of each is fine.")
                            if (existing == null) {
                                DocumentsPendingSave(isSaving)
                            } else {
                                SELLER_DOC_TYPES.forEach { docType ->
                                    DocumentUploadRow(
                                        label = docTypeLabel(docType),
                                        docStatus = documentsByType[docType]?.status,
                                        isUploading = uploadingDocType == docType,
                                        onPick = {
                                            pendingDocType = docType
                                            pickDocument.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // This screen has no bottom nav bar (approval is still pending, so the
        // outer nav host reserves no space for one), which means the system
        // navigation bar inset is otherwise unhandled — without this, the
        // Next/Submit button sits partly underneath it.
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (shownError != null) {
                OnboardingErrorBanner(shownError, onDismiss = { localError = null; onClearError() })
                Spacer(Modifier.height(12.dp))
            }

            if (step == STEP_DOCUMENTS) {
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    enabled = existing != null && allDocsUploaded && !isSubmitting && !isSaving
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Submit for review", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                if (existing != null && !allDocsUploaded) {
                    Spacer(Modifier.height(8.dp))
                    Text("Upload every document above to submit for review.", fontSize = 12.sp, color = TextLight)
                }
            } else {
                Button(
                    onClick = ::goNext,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    enabled = stepProblem == null && !isSaving
                ) {
                    if (isSaving && step == STEP_BANK) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.auth_next), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentsPendingSave(isSaving: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Teal)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = if (isSaving) "Saving your details…" else "Your details are being saved — this unlocks in a moment.",
            fontSize = 13.sp,
            color = TextLight
        )
    }
}

@Composable
private fun OnboardingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceWhite,
            unfocusedContainerColor = SurfaceWhite,
            focusedBorderColor = Teal,
            unfocusedBorderColor = BorderGray
        )
    )
}

@Composable
private fun OnboardingErrorBanner(message: String, onDismiss: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CoralSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, modifier = Modifier.weight(1f), color = CoralDark, fontSize = 13.sp)
            if (onDismiss != null) {
                Text(
                    "Dismiss",
                    color = CoralDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable { onDismiss() }
                )
            }
        }
    }
}
