package com.duggustore.app.data.repository

import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.remote.SupabaseService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val razorpayJson = Json { ignoreUnknownKeys = true }

/** A server-minted Razorpay order, ready to hand to the Checkout SDK. */
data class RazorpayOrder(val orderId: String, val amountPaise: Int, val keyId: String)

/**
 * Online-payment backend calls. Both halves of the money trust live in Edge
 * Functions (see supabase/functions/): order creation and HMAC signature
 * verification. The app never sees the key secret.
 */
class RazorpayOrderRepository {

    private fun token(): String? = SessionManager.getAccessToken()

    /**
     * Mints a Razorpay order for [amountPaise] (paise, not rupees). Fails when
     * the Edge Function isn't deployed or its secrets aren't set — the caller
     * falls back to COD in that case.
     */
    suspend fun createOrder(amountPaise: Long, receipt: String): Result<RazorpayOrder> {
        return try {
            val body = buildJsonObject {
                put("amount_paise", amountPaise)
                put("receipt", receipt.take(40))
            }.toString()
            val raw = SupabaseService.invokeFunction("create-razorpay-order", body, token())
            val obj = razorpayJson.parseToJsonElement(raw).jsonObject
            val orderId = obj["order_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (orderId.isBlank()) {
                val err = obj["error"]?.jsonPrimitive?.contentOrNull ?: "could not start the payment"
                return Result.failure(Exception(err))
            }
            Result.success(
                RazorpayOrder(
                    orderId = orderId,
                    amountPaise = obj["amount"]?.jsonPrimitive?.intOrNull ?: 0,
                    keyId = obj["key_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * True only when the Edge Function recomputes a matching HMAC signature.
     * The order is placed only on true — a faked client-side callback dies here.
     */
    suspend fun verifyPayment(orderId: String, paymentId: String, signature: String): Result<Boolean> {
        return try {
            val body = buildJsonObject {
                put("order_id", orderId)
                put("payment_id", paymentId)
                put("signature", signature)
            }.toString()
            val raw = SupabaseService.invokeFunction("verify-razorpay-payment", body, token())
            val obj = razorpayJson.parseToJsonElement(raw).jsonObject
            val valid = obj["valid"]?.jsonPrimitive?.contentOrNull == "true"
            Result.success(valid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
