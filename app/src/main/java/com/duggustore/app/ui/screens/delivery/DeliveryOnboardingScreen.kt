package com.duggustore.app.ui.screens.delivery

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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TwoWheeler
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
import com.duggustore.app.data.model.DELIVERY_DOC_TYPES
import com.duggustore.app.data.model.DeliveryPartner
import com.duggustore.app.data.model.DeliveryPartnerDocument
import com.duggustore.app.data.model.VEHICLE_TYPES
import com.duggustore.app.data.model.docTypeLabel
import com.duggustore.app.ui.components.DocumentUploadRow
import com.duggustore.app.ui.components.StepHeading
import com.duggustore.app.ui.components.StepIllustration
import com.duggustore.app.ui.components.StepProgressBar
import com.duggustore.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STEP_NAME = 0
private const val STEP_ADDRESS = 1
private const val STEP_EMERGENCY = 2
private const val STEP_ID = 3
private const val STEP_VEHICLE = 4
private const val STEP_BANK = 5
private const val STEP_DOCUMENTS = 6
private const val STEP_COUNT = 7

/**
 * A rider's application, one question per screen — same pattern as sign-up
 * and the seller wizard: a progress bar, a "Step X of Y" count, and a back
 * arrow that steps backward instead of one long form. Licence, ID, vehicle
 * and bank details, plus the documents that back them up.
 *
 * The application row has to exist before a document can be attached to it,
 * so moving on from the bank details step saves everything collected so
 * far — the documents step then unlocks once that save has landed.
 */
@Composable
fun DeliveryOnboardingScreen(
    userId: String,
    prefillEmail: String,
    prefillPhone: String,
    existing: DeliveryPartner?,
    documents: List<DeliveryPartnerDocument>,
    isSaving: Boolean,
    isSubmitting: Boolean,
    uploadingDocType: String?,
    error: String?,
    onUploadDocument: (docType: String, bytes: ByteArray, contentType: String) -> Unit,
    onSave: (DeliveryPartner) -> Unit,
    onSubmit: () -> Unit,
    onClearError: () -> Unit,
    onSignOut: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(STEP_NAME) }
    var movingForward by remember { mutableStateOf(true) }

    var fullName by remember(existing) { mutableStateOf(existing?.fullName.orEmpty()) }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: prefillPhone) }
    var licence by remember(existing) { mutableStateOf(existing?.licenceNumber.orEmpty()) }
    var aadhaar by remember(existing) { mutableStateOf(existing?.aadhaarNumber.orEmpty()) }
    var pan by remember(existing) { mutableStateOf(existing?.panNumber.orEmpty()) }
    var vehicleType by remember(existing) { mutableStateOf(existing?.vehicleType ?: VEHICLE_TYPES.first()) }
    var vehicleNumber by remember(existing) { mutableStateOf(existing?.vehicleNumber.orEmpty()) }
    var bankAccount by remember(existing) { mutableStateOf(existing?.bankAccountNumber.orEmpty()) }
    var bankIfsc by remember(existing) { mutableStateOf(existing?.bankIfsc.orEmpty()) }
    var upiId by remember(existing) { mutableStateOf(existing?.upiId.orEmpty()) }
    var city by remember(existing) { mutableStateOf(existing?.city.orEmpty()) }
    var address by remember(existing) { mutableStateOf(existing?.address.orEmpty()) }
    var emergencyName by remember(existing) { mutableStateOf(existing?.emergencyContactName.orEmpty()) }
    var emergencyPhone by remember(existing) { mutableStateOf(existing?.emergencyContactPhone.orEmpty()) }
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
    val allDocsUploaded = remember(documents) { DELIVERY_DOC_TYPES.all { documentsByType.containsKey(it) } }

    val stepProblem: String? = when (step) {
        STEP_NAME -> when {
            fullName.isBlank() -> "Enter your full name"
            phone.isBlank() -> "Enter a phone number"
            else -> null
        }
        STEP_ADDRESS -> if (address.isBlank()) "Enter your home address" else null
        STEP_ID -> when {
            licence.isBlank() -> "Enter your driving licence number"
            aadhaar.isBlank() -> "Enter your Aadhaar number"
            else -> null
        }
        STEP_VEHICLE -> if (vehicleNumber.isBlank()) "Enter your vehicle number" else null
        STEP_BANK -> when {
            bankAccount.isBlank() -> "Enter your bank account number"
            bankIfsc.isBlank() -> "Enter your bank IFSC code"
            else -> null
        }
        else -> null
    }

    val shownError = localError ?: error

    fun currentPartner() = DeliveryPartner(
        id = userId,
        fullName = fullName.trim(),
        email = prefillEmail,
        phone = phone.trim(),
        licenceNumber = licence.trim(),
        aadhaarNumber = aadhaar.trim(),
        panNumber = pan.trim().ifBlank { null },
        vehicleType = vehicleType,
        vehicleNumber = vehicleNumber.trim(),
        bankAccountNumber = bankAccount.trim(),
        bankIfsc = bankIfsc.trim(),
        upiId = upiId.trim().ifBlank { null },
        city = city.trim().ifBlank { null },
        address = address.trim(),
        emergencyContactName = emergencyName.trim().ifBlank { null },
        emergencyContactPhone = emergencyPhone.trim().ifBlank { null }
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
        if (step == STEP_BANK) onSave(currentPartner())
        if (step < STEP_COUNT - 1) {
            movingForward = true
            step++
        }
    }

    fun goBack() {
        localError = null
        onClearError()
        if (step > STEP_NAME) {
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
                    Text("Become a delivery partner", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Fill this in once — an admin reviews it before you can go online",
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
            if (step > STEP_NAME) {
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
                label = "delivery-onboarding-step"
            ) { s ->
                Column {
                    when (s) {
                        STEP_NAME -> {
                            StepHeading("Tell us about yourself", "This is what a store or customer will see when you deliver their order.")
                            OnboardingField(fullName, { fullName = it }, "Full name")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(phone, { phone = it }, "Phone number", keyboardType = KeyboardType.Phone)
                            StepIllustration(Icons.Default.Person, Teal, "Used for order pickups and drop confirmations.")
                        }
                        STEP_ADDRESS -> {
                            StepHeading("Where are you based?", "Helps us match you with orders nearby.")
                            OnboardingField(city, { city = it }, "City")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(address, { address = it }, "Home address")
                            StepIllustration(Icons.Default.LocationOn, Coral, "Kept private — never shown to customers or sellers.")
                        }
                        STEP_EMERGENCY -> {
                            StepHeading("Emergency contact", "Optional, but useful to have on file while you're out delivering.")
                            OnboardingField(emergencyName, { emergencyName = it }, "Contact name (optional)")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(emergencyPhone, { emergencyPhone = it }, "Contact phone (optional)", keyboardType = KeyboardType.Phone)
                            StepIllustration(Icons.Default.ContactPhone, Orange, "Only ever contacted if something goes wrong on a delivery.")
                        }
                        STEP_ID -> {
                            StepHeading("ID details", "PAN is optional — licence and Aadhaar are required to verify you.")
                            OnboardingField(licence, { licence = it.uppercase() }, "Driving licence number")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(aadhaar, { aadhaar = it }, "Aadhaar number", keyboardType = KeyboardType.Number)
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(pan, { pan = it.uppercase() }, "PAN number (optional)")
                            StepIllustration(Icons.Default.Badge, Violet, "Matched against your documents in the next step.")
                        }
                        STEP_VEHICLE -> {
                            StepHeading("Your vehicle", "So we know what kind of deliveries to send your way.")
                            Text("Vehicle type", fontSize = 12.sp, color = TextSecondary)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VEHICLE_TYPES.forEach { type ->
                                    VehicleTypeChip(
                                        label = type.lowercase().replaceFirstChar { it.uppercase() },
                                        selected = vehicleType == type,
                                        onClick = { vehicleType = type }
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(vehicleNumber, { vehicleNumber = it.uppercase() }, "Vehicle number")
                            StepIllustration(Icons.Default.TwoWheeler, Teal, "Checked against your vehicle RC and insurance.")
                        }
                        STEP_BANK -> {
                            StepHeading("Where should we pay you?", "Your delivery earnings go to this account.")
                            OnboardingField(bankAccount, { bankAccount = it }, "Bank account number", keyboardType = KeyboardType.Number)
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(bankIfsc, { bankIfsc = it.uppercase() }, "Bank IFSC code")
                            Spacer(Modifier.height(14.dp))
                            OnboardingField(upiId, { upiId = it }, "UPI ID (optional)")
                            StepIllustration(Icons.Default.AccountBalance, Orange, "Double-check these — payouts fail silently if they're wrong.")
                        }
                        else -> {
                            StepHeading("Upload your documents", "A clear photo of each is fine.")
                            if (existing == null) {
                                DocumentsPendingSave(isSaving)
                            } else {
                                DELIVERY_DOC_TYPES.forEach { docType ->
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
private fun VehicleTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Teal else SurfaceWhite,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else TextSecondary
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
