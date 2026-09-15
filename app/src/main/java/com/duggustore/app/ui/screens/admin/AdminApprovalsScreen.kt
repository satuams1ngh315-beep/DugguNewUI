package com.duggustore.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duggustore.app.data.model.DeliveryPartner
import com.duggustore.app.data.model.DeliveryPartnerDocument
import com.duggustore.app.data.model.Seller
import com.duggustore.app.data.model.SellerDocument
import com.duggustore.app.data.model.SponsoredSlot
import com.duggustore.app.data.model.VerificationStatus
import com.duggustore.app.data.model.docTypeLabel
import com.duggustore.app.ui.components.DashboardEmpty
import com.duggustore.app.ui.components.DashboardPanel
import com.duggustore.app.ui.theme.*

/**
 * The admin's KYC review queue — every seller and rider application currently
 * UNDER_REVIEW, with its submitted details and documents, and one approve/reject
 * decision per applicant. Nothing here shows once a decision is made: the
 * applicant then sees either their dashboard (approved) or the form again with
 * a rejection banner, so there is nothing left for the admin to act on.
 */
@Composable
fun AdminApprovalsScreen(
    sellers: List<Seller>,
    sellerDocuments: Map<String, List<SellerDocument>>,
    sellerDocumentUrls: Map<String, String>,
    loadingSellerDocsFor: String?,
    onLoadSellerDocuments: (sellerId: String) -> Unit,
    onReviewSeller: (sellerId: String, approve: Boolean, reason: String) -> Unit,
    reviewingSellerId: String?,
    sellerReviewError: String?,
    onClearSellerReviewError: () -> Unit,
    partners: List<DeliveryPartner>,
    partnerDocuments: Map<String, List<DeliveryPartnerDocument>>,
    partnerDocumentUrls: Map<String, String>,
    loadingPartnerDocsFor: String?,
    onLoadPartnerDocuments: (partnerId: String) -> Unit,
    onReviewPartner: (partnerId: String, approve: Boolean, reason: String) -> Unit,
    reviewingPartnerId: String?,
    partnerReviewError: String?,
    onClearPartnerReviewError: () -> Unit,
    sponsoredSlots: List<SponsoredSlot> = emptyList(),
    onReviewSponsoredSlot: (String, Boolean, String) -> Unit = { _, _, _ -> },
    reviewingSlotId: String? = null,
    slotReviewError: String? = null,
    onClearSlotReviewError: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val pendingSellers = remember(sellers) { sellers.filter { it.verificationStatus() == VerificationStatus.UNDER_REVIEW } }
    val pendingPartners = remember(partners) { partners.filter { it.verificationStatus() == VerificationStatus.UNDER_REVIEW } }
    val pendingSlots = remember(sponsoredSlots) { sponsoredSlots.filter { it.status == "PENDING" } }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ApprovalTabChip("Sellers (${pendingSellers.size})", tab == 0) { tab = 0 }
            ApprovalTabChip("Delivery (${pendingPartners.size})", tab == 1) { tab = 1 }
            ApprovalTabChip("Sponsored (${pendingSlots.size})", tab == 2) { tab = 2 }
        }

        val reviewError = when (tab) {
            0 -> sellerReviewError
            1 -> partnerReviewError
            else -> slotReviewError
        }
        if (reviewError != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = CoralSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(reviewError, modifier = Modifier.weight(1f), color = CoralDark, fontSize = 13.sp)
                    Text(
                        "Dismiss",
                        color = CoralDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable {
                                when (tab) {
                                    0 -> onClearSellerReviewError()
                                    1 -> onClearPartnerReviewError()
                                    else -> onClearSlotReviewError()
                                }
                            }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (tab == 0) {
            if (pendingSellers.isEmpty()) {
                DashboardEmpty(
                    icon = Icons.Default.VerifiedUser,
                    title = "No pending sellers",
                    subtitle = "Seller applications submitted for review will appear here"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pendingSellers, key = { it.id }) { seller ->
                        SellerApplicationCard(
                            seller = seller,
                            documents = sellerDocuments[seller.id].orEmpty(),
                            documentUrls = sellerDocumentUrls,
                            isLoadingDocs = loadingSellerDocsFor == seller.id,
                            isReviewing = reviewingSellerId == seller.id,
                            onExpand = { onLoadSellerDocuments(seller.id) },
                            onApprove = { onReviewSeller(seller.id, true, "") },
                            onReject = { reason -> onReviewSeller(seller.id, false, reason) }
                        )
                    }
                }
            }
        } else if (tab == 1) {
            if (pendingPartners.isEmpty()) {
                DashboardEmpty(
                    icon = Icons.Default.VerifiedUser,
                    title = "No pending riders",
                    subtitle = "Delivery partner applications submitted for review will appear here"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pendingPartners, key = { it.id }) { partner ->
                        DeliveryApplicationCard(
                            partner = partner,
                            documents = partnerDocuments[partner.id].orEmpty(),
                            documentUrls = partnerDocumentUrls,
                            isLoadingDocs = loadingPartnerDocsFor == partner.id,
                            isReviewing = reviewingPartnerId == partner.id,
                            onExpand = { onLoadPartnerDocuments(partner.id) },
                            onApprove = { onReviewPartner(partner.id, true, "") },
                            onReject = { reason -> onReviewPartner(partner.id, false, reason) }
                        )
                    }
                }
            }
        } else {
            if (pendingSlots.isEmpty()) {
                DashboardEmpty(
                    icon = Icons.Default.VerifiedUser,
                    title = "No pending requests",
                    subtitle = "Sponsored-slot requests from sellers will appear here"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pendingSlots, key = { it.id }) { slot ->
                        SponsoredSlotCard(
                            slot = slot,
                            isReviewing = reviewingSlotId == slot.id,
                            onApprove = { onReviewSponsoredSlot(slot.id, true, "") },
                            onReject = { reason -> onReviewSponsoredSlot(slot.id, false, reason) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerApplicationCard(
    seller: Seller,
    documents: List<SellerDocument>,
    documentUrls: Map<String, String>,
    isLoadingDocs: Boolean,
    isReviewing: Boolean,
    onExpand: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }

    DashboardPanel {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val opening = !expanded
                        expanded = opening
                        if (opening && documents.isEmpty()) onExpand()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(seller.businessName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(seller.ownerName, fontSize = 12.sp, color = TextSecondary)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = TextLight
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Divider(color = BorderGray)
                Spacer(Modifier.height(10.dp))
                ApplicationField("Phone", seller.phone)
                ApplicationField("PAN", seller.panNumber)
                ApplicationField("GST", seller.gstNumber)
                ApplicationField("FSSAI", seller.fssaiNumber)
                ApplicationField("Bank a/c", seller.bankAccountNumber)
                ApplicationField("IFSC", seller.bankIfsc)
                ApplicationField("UPI", seller.upiId)
                ApplicationField("Address", seller.businessAddress)

                ReviewDocumentsSection(documents.map { it.id to it.docType }, documentUrls, isLoadingDocs)

                Spacer(Modifier.height(14.dp))
                ApprovalActions(
                    isBusy = isReviewing,
                    onApprove = onApprove,
                    onReject = { showRejectDialog = true }
                )
            }
        }
    }

    if (showRejectDialog) {
        RejectDialog(
            onDismiss = { showRejectDialog = false },
            onConfirm = { reason -> showRejectDialog = false; onReject(reason) }
        )
    }
}

@Composable
private fun DeliveryApplicationCard(
    partner: DeliveryPartner,
    documents: List<DeliveryPartnerDocument>,
    documentUrls: Map<String, String>,
    isLoadingDocs: Boolean,
    isReviewing: Boolean,
    onExpand: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }

    DashboardPanel {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val opening = !expanded
                        expanded = opening
                        if (opening && documents.isEmpty()) onExpand()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(partner.fullName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        partner.vehicleType?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Rider",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = TextLight
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Divider(color = BorderGray)
                Spacer(Modifier.height(10.dp))
                ApplicationField("Phone", partner.phone)
                ApplicationField("City", partner.city)
                ApplicationField("Address", partner.address)
                ApplicationField("Licence", partner.licenceNumber)
                ApplicationField("Aadhaar", partner.aadhaarNumber)
                ApplicationField("PAN", partner.panNumber)
                ApplicationField("Vehicle no.", partner.vehicleNumber)
                ApplicationField("Bank a/c", partner.bankAccountNumber)
                ApplicationField("IFSC", partner.bankIfsc)
                ApplicationField("UPI", partner.upiId)
                ApplicationField("Emergency contact", partner.emergencyContactName)
                ApplicationField("Emergency phone", partner.emergencyContactPhone)

                ReviewDocumentsSection(documents.map { it.id to it.docType }, documentUrls, isLoadingDocs)

                Spacer(Modifier.height(14.dp))
                ApprovalActions(
                    isBusy = isReviewing,
                    onApprove = onApprove,
                    onReject = { showRejectDialog = true }
                )
            }
        }
    }

    if (showRejectDialog) {
        RejectDialog(
            onDismiss = { showRejectDialog = false },
            onConfirm = { reason -> showRejectDialog = false; onReject(reason) }
        )
    }
}

/**
 * No documents or expand/collapse here — a sponsored slot request is just
 * one of the seller's own products, a duration and a fee, so everything
 * worth reviewing already fits on the card. Collecting the fee happens
 * outside the app; approving here is only the content/legitimacy check
 * before it goes live.
 */
@Composable
private fun SponsoredSlotCard(
    slot: SponsoredSlot,
    isReviewing: Boolean,
    onApprove: () -> Unit,
    onReject: (String) -> Unit
) {
    var showRejectDialog by remember { mutableStateOf(false) }

    DashboardPanel {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                slot.product?.name ?: "Product no longer available",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (slot.headline.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(slot.headline, fontSize = 13.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "₹${slot.feeAmount} for ${slot.durationDays} days, starting once approved",
                fontSize = 11.sp,
                color = TextLight
            )
            Spacer(Modifier.height(14.dp))
            ApprovalActions(
                isBusy = isReviewing,
                onApprove = onApprove,
                onReject = { showRejectDialog = true }
            )
        }
    }

    if (showRejectDialog) {
        RejectDialog(
            onDismiss = { showRejectDialog = false },
            onConfirm = { reason -> showRejectDialog = false; onReject(reason) }
        )
    }
}

@Composable
private fun ReviewDocumentsSection(
    documents: List<Pair<String, String>>,
    documentUrls: Map<String, String>,
    isLoadingDocs: Boolean
) {
    Spacer(Modifier.height(10.dp))
    Text("Documents", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(Modifier.height(6.dp))
    when {
        isLoadingDocs -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Teal)
        documents.isEmpty() -> Text("No documents uploaded yet", fontSize = 12.sp, color = TextLight)
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            documents.forEach { (docId, docType) ->
                ReviewDocumentRow(label = docTypeLabel(docType), url = documentUrls[docId])
            }
        }
    }
}

@Composable
private fun ReviewDocumentRow(label: String, url: String?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceMuted,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(SurfaceWhite),
                contentAlignment = Alignment.Center
            ) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Teal)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ApprovalActions(isBusy: Boolean, onApprove: () -> Unit, onReject: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onReject,
            enabled = !isBusy,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralDark)
        ) {
            Text("Reject")
        }
        Button(
            onClick = onApprove,
            enabled = !isBusy,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
        ) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Approve", color = Color.White)
            }
        }
    }
}

@Composable
private fun ApplicationField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, color = TextLight, modifier = Modifier.width(110.dp))
        Text(value, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ApprovalTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Teal else SurfaceMuted,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun RejectDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject application") },
        text = {
            Column {
                Text("This is shown to the applicant so they know what to fix.", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("Reason for rejection", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = reason.isNotBlank()) {
                Text("Reject", color = CoralDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
