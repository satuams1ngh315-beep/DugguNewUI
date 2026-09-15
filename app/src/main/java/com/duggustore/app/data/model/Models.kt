package com.duggustore.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class UserRole(val value: String) {
    @SerialName("customer") CUSTOMER("customer"),
    @SerialName("seller") SELLER("seller"),
    @SerialName("delivery") DELIVERY("delivery"),
    @SerialName("admin") ADMIN("admin");

    companion object {
        fun fromString(value: String): UserRole = when (value) {
            "seller" -> SELLER
            "delivery" -> DELIVERY
            "admin" -> ADMIN
            else -> CUSTOMER
        }
    }
}

enum class OrderStatus(val value: String) {
    @SerialName("pending") PENDING("pending"),
    @SerialName("confirmed") CONFIRMED("confirmed"),
    @SerialName("preparing") PREPARING("preparing"),
    // Packed and waiting for a rider — nobody is carrying it yet. Distinct from
    // OUT_FOR_DELIVERY, which means a specific rider has claimed it and it is
    // actually moving; the tracking card on the customer's order screen is
    // gated on that difference.
    @SerialName("ready_for_pickup") READY_FOR_PICKUP("ready_for_pickup"),
    @SerialName("out_for_delivery") OUT_FOR_DELIVERY("out_for_delivery"),
    @SerialName("delivered") DELIVERED("delivered"),
    @SerialName("cancelled") CANCELLED("cancelled");

    companion object {
        fun fromString(value: String): OrderStatus = when (value) {
            "confirmed" -> CONFIRMED
            "preparing" -> PREPARING
            "ready_for_pickup" -> READY_FOR_PICKUP
            "out_for_delivery" -> OUT_FOR_DELIVERY
            "delivered" -> DELIVERED
            "cancelled" -> CANCELLED
            else -> PENDING
        }
    }

    fun displayText(): String = when (this) {
        PENDING -> "Pending"
        CONFIRMED -> "Confirmed"
        PREPARING -> "Preparing"
        READY_FOR_PICKUP -> "Ready for Pickup"
        OUT_FOR_DELIVERY -> "Out for Delivery"
        DELIVERED -> "Delivered"
        CANCELLED -> "Cancelled"
    }
}

@Serializable
data class UserProfile(
    val id: String = "",
    @SerialName("full_name") val fullName: String = "",
    val phone: String = "",
    val role: String = "customer",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    // Only meaningful for role == "seller" — where the rider picks up from.
    @SerialName("store_address") val storeAddress: String? = null,
    @SerialName("store_latitude") val storeLatitude: Double? = null,
    @SerialName("store_longitude") val storeLongitude: Double? = null,
    // Only meaningful for role == "delivery" — whether this rider wants new
    // pool orders to reach them right now.
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("referral_code") val referralCode: String = "",
    @SerialName("referred_by") val referredBy: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    fun userRole(): UserRole = UserRole.fromString(role)
    fun hasStoreFix(): Boolean = storeLatitude != null && storeLongitude != null
}

@Serializable
data class Category(
    val id: String = "",
    val name: String = "",
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("color_hex") val colorHex: String = "#7C3AED",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true
)

// @Immutable because it holds a List (image_urls), which the Compose
// compiler otherwise infers as unstable — every product card in Home's
// feed would then be ineligible for recomposition-skipping, so any
// unrelated state change anywhere in the screen's tree (a focus change,
// the notification badge ticking, cart quantities updating) could force
// every visible card to recompose while the user is mid-scroll. Products
// arrive from the server and are only ever replaced, never mutated in
// place, so the annotation is accurate rather than a promise we break.
@Immutable
@Serializable
data class Product(
    val id: String = "",
    @SerialName("seller_id") val sellerId: String = "",
    @SerialName("category_id") val categoryId: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    @SerialName("discount_price") val discountPrice: Double? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    // Added alongside image_url rather than replacing it — every reader that
    // only knows about a single photo (dashboards, order rows) keeps working
    // off image_url, which is always kept as the first entry here.
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    // Server-side processed cutout image with background removed
    // Used for better performance in offer carousel instead of on-device ML Kit
    @SerialName("cutout_image_url") val cutoutImageUrl: String? = null,
    val stock: Int = 0,
    val unit: String = "pcs",
    @SerialName("is_active") val isActive: Boolean = true,
    // Null for anything that isn't food at all (a cleaning product, a
    // shampoo) — the veg/non-veg mark only shows up once a seller has
    // actually tagged a product one way or the other.
    @SerialName("is_veg") val isVeg: Boolean? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    fun effectivePrice(): Double = discountPrice ?: price
    fun hasDiscount(): Boolean = discountPrice != null && discountPrice < price
    fun savingsAmount(): Double = if (hasDiscount()) price - discountPrice!! else 0.0

    /** All of the product's photos, falling back to the single legacy field for older rows. */
    fun images(): List<String> = imageUrls.ifEmpty { listOfNotNull(imageUrl?.takeIf { it.isNotBlank() }) }
    
    /** Get the cutout image URL if available, otherwise fall back to first regular image */
    fun cutoutImage(): String? = cutoutImageUrl?.takeIf { it.isNotBlank() } ?: images().firstOrNull()
}

@Serializable
data class CartItem(
    val id: String = "",
    @SerialName("customer_id") val customerId: String = "",
    @SerialName("product_id") val productId: String = "",
    val quantity: Int = 1,
    val product: Product? = null
)

@Serializable
data class Order(
    val id: String = "",
    @SerialName("customer_id") val customerId: String = "",
    @SerialName("seller_id") val sellerId: String = "",
    @SerialName("delivery_id") val deliveryId: String? = null,
    val status: String = "pending",
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("delivery_fee") val deliveryFee: Double = 0.0,
    @SerialName("delivery_address") val deliveryAddress: String = "",
    // Captured from the customer's selected address at checkout, when that
    // address itself carries a fix — null on any order placed before this,
    // or against an address that was only ever typed in by hand.
    @SerialName("delivery_latitude") val deliveryLatitude: Double? = null,
    @SerialName("delivery_longitude") val deliveryLongitude: Double? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("payment_method") val paymentMethod: String = "cod",
    @SerialName("wallet_used") val walletUsed: Double = 0.0,
    // Razorpay payment id on online orders — null on COD. Only ever set after
    // the verify-razorpay-payment Edge Function confirms the signature, so a
    // faked client-side "success" can never mark an order as paid.
    @SerialName("payment_id") val paymentId: String? = null,
    val items: List<OrderItem> = emptyList(),
    // Embedded via a PostgREST select on the matching FK; each is only
    // populated by the order queries that actually ask for it — the seller's
    // store for a rider's pickup, the customer's phone for a rider or
    // seller to call, the rider's phone for a customer to call once one is
    // assigned.
    val seller: SellerStore? = null,
    val customer: ContactInfo? = null,
    val delivery: ContactInfo? = null
) {
    fun orderStatus(): OrderStatus = OrderStatus.fromString(status)
    fun hasDeliveryFix(): Boolean = deliveryLatitude != null && deliveryLongitude != null
}

@Serializable
data class SellerStore(
    @SerialName("full_name") val fullName: String = "",
    val phone: String = "",
    @SerialName("store_address") val storeAddress: String? = null,
    @SerialName("store_latitude") val storeLatitude: Double? = null,
    @SerialName("store_longitude") val storeLongitude: Double? = null
) {
    fun hasFix(): Boolean = storeLatitude != null && storeLongitude != null
}

/** A person on the other end of an order worth calling — the customer, or the rider once one has claimed it. */
@Serializable
data class ContactInfo(
    @SerialName("full_name") val fullName: String = "",
    val phone: String = ""
)

@Serializable
data class OrderItem(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("product_id") val productId: String = "",
    val quantity: Int = 1,
    @SerialName("price_at_purchase") val priceAtPurchase: Double = 0.0,
    val product: Product? = null
)

@Serializable
data class Review(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("product_id") val productId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

/** A customer's report of a problem with a delivered order/item, resolved by the seller or an admin. */
@Serializable
data class OrderIssue(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("product_id") val productId: String? = null,
    @SerialName("user_id") val userId: String = "",
    val reason: String = "",
    val description: String = "",
    val status: String = "open",
    @SerialName("refund_amount") val refundAmount: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("resolved_at") val resolvedAt: String? = null
)

/** One line of the wallet ledger — the balance itself is derived by summing these, never stored. */
@Serializable
data class WalletTransaction(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val title: String = "",
    val amount: Int = 0,
    val type: String = "CREDIT",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("refilled") val refilled: Boolean = false
)

@Serializable
data class DeliveryTracking(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("delivery_id") val deliveryId: String = "",
    val status: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("updated_at") val updatedAt: String = ""
) {
    /** 0,0 is the column default, so it means "never reported" rather than a place. */
    fun hasFix(): Boolean = latitude != 0.0 || longitude != 0.0
}

@Serializable
data class Address(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val label: String = "",
    @SerialName("full_address") val fullAddress: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("is_default") val isDefault: Boolean = false
)

@Serializable
data class Favorite(
    val id: String = "",
    @SerialName("customer_id") val customerId: String = "",
    @SerialName("product_id") val productId: String = ""
)

/** Shared by seller and delivery-partner applications — both tables use the same five values. */
enum class VerificationStatus(val value: String) {
    PENDING_VERIFICATION("PENDING_VERIFICATION"),
    UNDER_REVIEW("UNDER_REVIEW"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    SUSPENDED("SUSPENDED");

    companion object {
        fun fromString(value: String): VerificationStatus = when (value) {
            "UNDER_REVIEW" -> UNDER_REVIEW
            "APPROVED" -> APPROVED
            "REJECTED" -> REJECTED
            "SUSPENDED" -> SUSPENDED
            else -> PENDING_VERIFICATION
        }
    }
}

/** The document types the seller-documents table's check constraint accepts, in the order collected. */
val SELLER_DOC_TYPES = listOf("PAN", "GST_CERTIFICATE", "FSSAI_LICENSE", "BANK_PROOF", "ADDRESS_PROOF")

/** Same, for delivery_partner_documents. */
val DELIVERY_DOC_TYPES = listOf("DRIVING_LICENCE", "AADHAAR", "PAN", "VEHICLE_RC", "VEHICLE_INSURANCE", "BANK_PROOF")

val VEHICLE_TYPES = listOf("BIKE", "SCOOTER", "BICYCLE", "EV_SCOOTER")

fun docTypeLabel(docType: String): String = when (docType) {
    "PAN" -> "PAN card"
    "GST_CERTIFICATE" -> "GST certificate"
    "FSSAI_LICENSE" -> "FSSAI licence"
    "BANK_PROOF" -> "Bank proof (cancelled cheque)"
    "ADDRESS_PROOF" -> "Business address proof"
    "DRIVING_LICENCE" -> "Driving licence"
    "AADHAAR" -> "Aadhaar card"
    "VEHICLE_RC" -> "Vehicle registration (RC)"
    "VEHICLE_INSURANCE" -> "Vehicle insurance"
    else -> docType
}

/** A seller's business KYC — separate from the customer-facing `profiles` row, gates whether they can list products. */
@Serializable
data class Seller(
    val id: String = "",
    @SerialName("business_name") val businessName: String = "",
    @SerialName("owner_name") val ownerName: String = "",
    val email: String = "",
    val phone: String? = null,
    @SerialName("pan_number") val panNumber: String? = null,
    @SerialName("gst_number") val gstNumber: String? = null,
    @SerialName("fssai_number") val fssaiNumber: String? = null,
    @SerialName("bank_account_number") val bankAccountNumber: String? = null,
    @SerialName("bank_ifsc") val bankIfsc: String? = null,
    @SerialName("upi_id") val upiId: String? = null,
    @SerialName("business_address") val businessAddress: String? = null,
    val status: String = "PENDING_VERIFICATION",
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    fun verificationStatus(): VerificationStatus = VerificationStatus.fromString(status)
    fun isApproved(): Boolean = status == "APPROVED"
}

@Serializable
data class SellerDocument(
    val id: String = "",
    @SerialName("seller_id") val sellerId: String = "",
    @SerialName("doc_type") val docType: String = "",
    @SerialName("file_url") val fileUrl: String = "",
    val status: String = "PENDING",
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

/** A rider's KYC — gates whether they can go online for pool orders. */
@Serializable
data class DeliveryPartner(
    val id: String = "",
    @SerialName("full_name") val fullName: String = "",
    val email: String = "",
    val phone: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("licence_number") val licenceNumber: String? = null,
    @SerialName("aadhaar_number") val aadhaarNumber: String? = null,
    @SerialName("pan_number") val panNumber: String? = null,
    @SerialName("vehicle_type") val vehicleType: String? = null,
    @SerialName("vehicle_number") val vehicleNumber: String? = null,
    @SerialName("bank_account_number") val bankAccountNumber: String? = null,
    @SerialName("bank_ifsc") val bankIfsc: String? = null,
    @SerialName("upi_id") val upiId: String? = null,
    val city: String? = null,
    val address: String? = null,
    @SerialName("emergency_contact_name") val emergencyContactName: String? = null,
    @SerialName("emergency_contact_phone") val emergencyContactPhone: String? = null,
    val status: String = "PENDING_VERIFICATION",
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String = ""
) {
    fun verificationStatus(): VerificationStatus = VerificationStatus.fromString(status)
    fun isApproved(): Boolean = status == "APPROVED"
}

@Serializable
data class DeliveryPartnerDocument(
    val id: String = "",
    @SerialName("partner_id") val partnerId: String = "",
    @SerialName("doc_type") val docType: String = "",
    @SerialName("file_url") val fileUrl: String = "",
    val status: String = "PENDING",
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

// Wrapper classes for Supabase Postgrest
@Serializable
data class ProfileResponse(val profiles: List<UserProfile> = emptyList())

@Serializable
data class CategoryResponse(val categories: List<Category> = emptyList())

@Serializable
data class Coupon(
    val id: String = "",
    val code: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("max_discount") val maxDiscount: Int = 0,
    @SerialName("min_order_value") val minOrderValue: Int = 0,
    @SerialName("expiry_label") val expiryLabel: String = "",
    @SerialName("is_active") val isActive: Boolean = true
)

/** An admin-run seasonal push (e.g. "Diwali Dhamaka") pointing at one category, running for a fixed window rather than tied to a discount. */
@Serializable
data class Campaign(
    val id: String = "",
    val label: String = "",
    @SerialName("tint_hex") val tintHex: String = "#F5A623",
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("cta_label") val ctaLabel: String = "SHOP NOW",
    @SerialName("starts_at") val startsAt: String = "",
    @SerialName("ends_at") val endsAt: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String = ""
)

/**
 * A seller-paid placement on the home rail, promoting one specific product
 * the seller already lists — requested by the seller, only live once an
 * admin approves it. [startsAt]/[endsAt] are null until then: the window is
 * stamped at approval time so review delay never eats into the
 * [durationDays] the seller asked for. [feeAmount] is quoted and frozen at
 * request time, so it stays what both sides agreed even if the per-day
 * rate changes later.
 */
@Serializable
data class SponsoredSlot(
    val id: String = "",
    @SerialName("seller_id") val sellerId: String = "",
    @SerialName("product_id") val productId: String = "",
    /** Embedded via a PostgREST select — the product being promoted. */
    val product: Product? = null,
    /** An optional short note from the seller shown alongside the product. */
    val headline: String = "",
    @SerialName("duration_days") val durationDays: Int = 7,
    @SerialName("fee_amount") val feeAmount: Int = 0,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val status: String = "PENDING",
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

/**
 * One titled block of the home page's browse layout — "Bestsellers",
 * "Grocery & Kitchen". Admin-curated: the admin decides the title, the
 * layout, and which categories sit in it. What fills the tiles is not
 * curated at all — the photos and counts come from whatever products
 * sellers currently have live in those categories.
 *
 * @Immutable because it is read-only once loaded and holds a List, which
 * Compose otherwise treats as unstable — that made every section block
 * recompose whenever anything else on Home changed.
 */
@Immutable
@Serializable
data class HomeSection(
    val id: String = "",
    val title: String = "",
    /** [LAYOUT_COLLAGE] or [LAYOUT_TILE]. */
    val layout: String = LAYOUT_TILE,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    /** Filled by the home_layout RPC; empty on rows read straight from the table. */
    val categories: List<HomeSectionCategory> = emptyList()
) {
    companion object {
        /** Four product photos in a 2×2 with the category name under them. */
        const val LAYOUT_COLLAGE = "collage"

        /** One photo per category, four across. */
        const val LAYOUT_TILE = "tile"
    }
}

/** A category as it appears inside a home section, with its own live product tally. */
@Immutable
@Serializable
data class HomeSectionCategory(
    val id: String = "",
    val name: String = "",
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("color_hex") val colorHex: String = "#7C3AED",
    @SerialName("product_count") val productCount: Int = 0,
    /** Up to four, newest first, and only from products that actually have a photo. */
    @SerialName("preview_images") val previewImages: List<String> = emptyList()
)

@Serializable
data class ProductResponse(val products: List<Product> = emptyList())

@Serializable
data class CartResponse(val cart_items: List<CartItem> = emptyList())

@Serializable
data class OrderResponse(val orders: List<Order> = emptyList())

@Serializable
data class FavoriteResponse(val favorites: List<Favorite> = emptyList())

/** Row count and most recent write for one table, from the admin_table_stats() RPC. */
@Serializable
data class TableStat(
    @SerialName("table_name") val tableName: String = "",
    @SerialName("row_count") val rowCount: Long = 0,
    /** Null for tables that have no created_at column to read. */
    @SerialName("last_created") val lastCreated: String? = null
)

/**
 * Headline database figures from the admin_db_health() RPC.
 *
 * The second group are queues that need a human — they are what the Database
 * tab highlights, rather than the raw totals already on the dashboard header.
 */
@Serializable
data class DbHealth(
    @SerialName("signups_today") val signupsToday: Int = 0,
    @SerialName("signups_week") val signupsWeek: Int = 0,
    @SerialName("orders_today") val ordersToday: Int = 0,
    @SerialName("orders_week") val ordersWeek: Int = 0,
    @SerialName("revenue_today") val revenueToday: Double = 0.0,
    @SerialName("revenue_week") val revenueWeek: Double = 0.0,
    @SerialName("pending_sellers") val pendingSellers: Int = 0,
    @SerialName("pending_partners") val pendingPartners: Int = 0,
    @SerialName("open_issues") val openIssues: Int = 0,
    @SerialName("stuck_orders") val stuckOrders: Int = 0,
    @SerialName("out_of_stock") val outOfStock: Int = 0,
    @SerialName("orphan_sellers") val orphanSellers: Int = 0
)
