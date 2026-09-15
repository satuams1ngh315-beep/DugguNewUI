package com.duggustore.app.platform

import android.app.Activity
import com.duggustore.app.BuildConfig
import com.razorpay.Checkout
import com.razorpay.PaymentData
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/**
 * Whatever the Razorpay Checkout SDK reported for the last payment attempt.
 * Posted by MainActivity (which implements Razorpay's result listener) and
 * consumed once by the checkout flow in NavGraph.
 */
sealed interface RazorpayResult {
    data class Success(
        val razorpayOrderId: String,
        val paymentId: String,
        val signature: String
    ) : RazorpayResult

    data class Failure(val code: Int, val description: String) : RazorpayResult

    /** Checkout was dismissed / never opened — the order simply isn't placed. */
    data object Dismissed : RazorpayResult
}

object RazorpayResultBus {
    val results = MutableStateFlow<RazorpayResult?>(null)
    fun post(result: RazorpayResult) { results.value = result }
    fun consume() { results.value = null }
}

object RazorpayPay {

    /**
     * The online option only shows when a real key id was baked in at build
     * time — without RAZORPAY_KEY_ID in local.properties the checkout stays
     * exactly as it was: COD + wallet.
     */
    fun isAvailable(): Boolean = BuildConfig.RAZORPAY_KEY_ID.isNotBlank()

    /**
     * Opens the Razorpay sheet (UPI / cards / netbanking / wallets) for an
     * order the create-razorpay-order Edge Function already minted. The amount
     * shown comes from that server-side order, not from this call — [amountPaise]
     * is only a display fallback if [razorpayOrderId] is somehow blank.
     */
    fun startPayment(
        activity: Activity,
        razorpayOrderId: String,
        keyId: String,
        amountPaise: Long,
        storeName: String,
        description: String,
        prefillEmail: String? = null,
        prefillContact: String? = null
    ) {
        val checkout = Checkout()
        checkout.setKeyID(keyId.ifBlank { BuildConfig.RAZORPAY_KEY_ID })

        val options = JSONObject().apply {
            put("name", storeName)
            put("description", description)
            put("currency", "INR")
            put("amount", amountPaise)
            if (razorpayOrderId.isNotBlank()) put("order_id", razorpayOrderId)
            put("theme", JSONObject().put("color", "#0D3B2E"))
            val prefill = JSONObject()
            if (!prefillEmail.isNullOrBlank()) prefill.put("email", prefillEmail)
            if (!prefillContact.isNullOrBlank()) prefill.put("contact", prefillContact)
            put("prefill", prefill)
            put("retry", JSONObject().put("enabled", true).put("max_count", 3))
        }

        try {
            checkout.open(activity, options)
        } catch (e: Exception) {
            RazorpayResultBus.post(
                RazorpayResult.Failure(-1, e.message ?: "Could not open the payment sheet")
            )
        }
    }

    /** Success callback from MainActivity.onPaymentSuccess — parses Razorpay's payload. */
    fun postSuccess(data: PaymentData) {
        RazorpayResultBus.post(
            RazorpayResult.Success(
                razorpayOrderId = data.orderId.orEmpty(),
                paymentId = data.paymentId.orEmpty(),
                signature = data.signature.orEmpty()
            )
        )
    }

    /** Error callback from MainActivity.onPaymentError. */
    fun postFailure(code: Int, description: String?) {
        val desc = description.orEmpty()
        // Razorpay reports a user-dismissed sheet as an error ("Payment
        // cancelled by user"); treat that as a quiet cancel rather than a
        // scary failure. Description-matched on purpose — no SDK error-code
        // constant to drift against.
        if (desc.contains("cancel", ignoreCase = true) ||
            desc.contains("dismiss", ignoreCase = true)
        ) {
            RazorpayResultBus.post(RazorpayResult.Dismissed)
        } else {
            RazorpayResultBus.post(
                RazorpayResult.Failure(code, desc.ifBlank { "Payment failed — try again" })
            )
        }
    }
}
