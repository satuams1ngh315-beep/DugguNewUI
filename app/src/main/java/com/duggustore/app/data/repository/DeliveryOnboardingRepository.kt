package com.duggustore.app.data.repository

import com.duggustore.app.data.model.DeliveryPartner
import com.duggustore.app.data.model.DeliveryPartnerDocument
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class DeliveryOnboardingRepository {
    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun getMyPartner(userId: String): Result<DeliveryPartner?> {
        return try {
            val list = SupabaseService.select("delivery_partners", token(), mapOf("id" to userId))
            Result.success(list.firstOrNull()?.let { json.decodeFromString(DeliveryPartner.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Same pattern as SellerOnboardingRepository.saveApplication: status and
     * rejection_reason are never sent, so a rider can revise their details
     * freely up until [submitForReview] without touching either column.
     */
    suspend fun saveApplication(partner: DeliveryPartner): Result<Unit> {
        return try {
            val token = token()
            val body = buildJsonObject {
                put("id", partner.id)
                put("full_name", partner.fullName)
                put("email", partner.email)
                put("phone", partner.phone)
                put("date_of_birth", partner.dateOfBirth)
                put("licence_number", partner.licenceNumber)
                put("aadhaar_number", partner.aadhaarNumber)
                put("pan_number", partner.panNumber)
                put("vehicle_type", partner.vehicleType)
                put("vehicle_number", partner.vehicleNumber)
                put("bank_account_number", partner.bankAccountNumber)
                put("bank_ifsc", partner.bankIfsc)
                put("upi_id", partner.upiId)
                put("city", partner.city)
                put("address", partner.address)
                put("emergency_contact_name", partner.emergencyContactName)
                put("emergency_contact_phone", partner.emergencyContactPhone)
            }.toString()
            SupabaseService.upsert("delivery_partners", body, onConflict = "id", token)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDocuments(partnerId: String): Result<List<DeliveryPartnerDocument>> {
        return try {
            val list = SupabaseService.select("delivery_partner_documents", token(), mapOf("partner_id" to partnerId))
            Result.success(list.map { json.decodeFromString(DeliveryPartnerDocument.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadDocument(partnerId: String, docType: String, bytes: ByteArray, contentType: String): Result<Unit> {
        return try {
            val token = token()
            val ext = if (contentType == "application/pdf") "pdf" else "jpg"
            val path = "$partnerId/$docType.$ext"
            SupabaseService.uploadPrivateFile("delivery-documents", path, bytes, contentType, token)
            val body = buildJsonObject {
                put("partner_id", partnerId)
                put("doc_type", docType)
                put("file_url", path)
                put("status", "PENDING")
            }.toString()
            SupabaseService.upsert("delivery_partner_documents", body, onConflict = "partner_id,doc_type", token)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun documentUrl(path: String): Result<String> {
        return try {
            Result.success(SupabaseService.signedUrl("delivery-documents", path, token()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitForReview(partnerId: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("p_partner_id", partnerId) }.toString()
            SupabaseService.rpc("submit_delivery_partner_for_review", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Admin-only in practice — RLS only lets an admin session see every row. */
    suspend fun getAllPartners(): Result<List<DeliveryPartner>> {
        return try {
            Result.success(
                SupabaseService.selectAll("delivery_partners", token())
                    .map { json.decodeFromString(DeliveryPartner.serializer(), it.toString()) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reviewPartner(partnerId: String, approve: Boolean, rejectionReason: String = ""): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("p_partner_id", partnerId)
                put("p_approve", approve)
                put("p_rejection_reason", rejectionReason)
            }.toString()
            SupabaseService.rpc("review_delivery_partner", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
