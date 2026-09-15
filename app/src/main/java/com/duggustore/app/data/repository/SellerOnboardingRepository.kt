package com.duggustore.app.data.repository

import com.duggustore.app.data.model.Seller
import com.duggustore.app.data.model.SellerDocument
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class SellerOnboardingRepository {
    private fun token(): String? = SessionManager.getAccessToken()

    suspend fun getMySeller(userId: String): Result<Seller?> {
        return try {
            val list = SupabaseService.select("sellers", token(), mapOf("id" to userId))
            Result.success(list.firstOrNull()?.let { json.decodeFromString(Seller.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates the application (status defaults to PENDING_VERIFICATION on
     * the table) or updates the still-editable one — the seller can revise
     * these fields freely up until they call [submitForReview]. status and
     * rejection_reason are never sent from here: the table's own trigger
     * would just revert them if they were, since only the review RPCs may
     * change those two columns.
     */
    suspend fun saveApplication(seller: Seller): Result<Unit> {
        return try {
            val token = token()
            val body = buildJsonObject {
                put("id", seller.id)
                put("business_name", seller.businessName)
                put("owner_name", seller.ownerName)
                put("email", seller.email)
                put("phone", seller.phone)
                put("pan_number", seller.panNumber)
                put("gst_number", seller.gstNumber)
                put("fssai_number", seller.fssaiNumber)
                put("bank_account_number", seller.bankAccountNumber)
                put("bank_ifsc", seller.bankIfsc)
                put("upi_id", seller.upiId)
                put("business_address", seller.businessAddress)
            }.toString()
            SupabaseService.upsert("sellers", body, onConflict = "id", token)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDocuments(sellerId: String): Result<List<SellerDocument>> {
        return try {
            val list = SupabaseService.select("seller_documents", token(), mapOf("seller_id" to sellerId))
            Result.success(list.map { json.decodeFromString(SellerDocument.serializer(), it.toString()) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** One row per doc_type — re-uploading the same type overwrites both the file and the row (upsert on seller_id+doc_type). */
    suspend fun uploadDocument(sellerId: String, docType: String, bytes: ByteArray, contentType: String): Result<Unit> {
        return try {
            val token = token()
            val ext = if (contentType == "application/pdf") "pdf" else "jpg"
            val path = "$sellerId/$docType.$ext"
            SupabaseService.uploadPrivateFile("seller-documents", path, bytes, contentType, token)
            val body = buildJsonObject {
                put("seller_id", sellerId)
                put("doc_type", docType)
                put("file_url", path)
                put("status", "PENDING")
            }.toString()
            SupabaseService.upsert("seller_documents", body, onConflict = "seller_id,doc_type", token)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun documentUrl(path: String): Result<String> {
        return try {
            Result.success(SupabaseService.signedUrl("seller-documents", path, token()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitForReview(sellerId: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("p_seller_id", sellerId) }.toString()
            SupabaseService.rpc("submit_seller_for_review", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Admin-only in practice — RLS only lets an admin session see every row. */
    suspend fun getAllSellers(): Result<List<Seller>> {
        return try {
            Result.success(
                SupabaseService.selectAll("sellers", token())
                    .map { json.decodeFromString(Seller.serializer(), it.toString()) }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Approves or rejects an application that's currently UNDER_REVIEW, via the review_seller() RPC. */
    suspend fun reviewSeller(sellerId: String, approve: Boolean, rejectionReason: String = ""): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("p_seller_id", sellerId)
                put("p_approve", approve)
                put("p_rejection_reason", rejectionReason)
            }.toString()
            SupabaseService.rpc("review_seller", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Blocks (SUSPENDED), unblocks (APPROVED), or soft-deletes (REJECTED) a seller. Any of the three also hides their products when moving to SUSPENDED/REJECTED. */
    suspend fun setSellerStatus(sellerId: String, status: String): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("p_seller_id", sellerId)
                put("p_status", status)
            }.toString()
            SupabaseService.rpc("admin_set_seller_status", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Irreversible: wipes the seller's entire account — profile, seller record, documents, products, and order history. */
    suspend fun purgeSeller(sellerId: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("p_seller_id", sellerId) }.toString()
            SupabaseService.rpc("admin_purge_seller", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Turns an existing account into an approved seller instantly, skipping document review. */
    suspend fun promoteToSeller(userId: String): Result<Unit> {
        return try {
            val body = buildJsonObject { put("p_user_id", userId) }.toString()
            SupabaseService.rpc("admin_promote_to_seller", body, token())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
