package com.duggustore.app

import com.duggustore.app.data.model.DELIVERY_DOC_TYPES
import com.duggustore.app.data.model.Order
import com.duggustore.app.data.model.OrderStatus
import com.duggustore.app.data.model.Product
import com.duggustore.app.data.model.SELLER_DOC_TYPES
import com.duggustore.app.data.model.Seller
import com.duggustore.app.data.model.SellerStore
import com.duggustore.app.data.model.UserRole
import com.duggustore.app.data.model.VEHICLE_TYPES
import com.duggustore.app.data.model.VerificationStatus
import com.duggustore.app.data.model.docTypeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing + helpers on the data models — no Android, no backend needed. */
class ModelsTest {

    // ---- OrderStatus ----

    @Test
    fun orderStatus_parsesAllSevenValues() {
        assertEquals(OrderStatus.PENDING, OrderStatus.fromString("pending"))
        assertEquals(OrderStatus.CONFIRMED, OrderStatus.fromString("confirmed"))
        assertEquals(OrderStatus.PREPARING, OrderStatus.fromString("preparing"))
        assertEquals(OrderStatus.READY_FOR_PICKUP, OrderStatus.fromString("ready_for_pickup"))
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.fromString("out_for_delivery"))
        assertEquals(OrderStatus.DELIVERED, OrderStatus.fromString("delivered"))
        assertEquals(OrderStatus.CANCELLED, OrderStatus.fromString("cancelled"))
    }

    @Test
    fun orderStatus_unknownFallsBackToPending() {
        assertEquals(OrderStatus.PENDING, OrderStatus.fromString(""))
        assertEquals(OrderStatus.PENDING, OrderStatus.fromString("shipped"))
        assertEquals(OrderStatus.PENDING, OrderStatus.fromString("PENDING"))
    }

    @Test
    fun orderStatus_displayTextCoversEveryValue() {
        OrderStatus.values().forEach { assertTrue(it.displayText().isNotBlank()) }
        assertEquals("Out for Delivery", OrderStatus.OUT_FOR_DELIVERY.displayText())
        assertEquals("Ready for Pickup", OrderStatus.READY_FOR_PICKUP.displayText())
    }

    // ---- UserRole / VerificationStatus ----

    @Test
    fun userRole_parsesAndDefaultsToCustomer() {
        assertEquals(UserRole.SELLER, UserRole.fromString("seller"))
        assertEquals(UserRole.DELIVERY, UserRole.fromString("delivery"))
        assertEquals(UserRole.ADMIN, UserRole.fromString("admin"))
        assertEquals(UserRole.CUSTOMER, UserRole.fromString("customer"))
        assertEquals(UserRole.CUSTOMER, UserRole.fromString("superadmin"))
        assertEquals(UserRole.CUSTOMER, UserRole.fromString(""))
    }

    @Test
    fun verificationStatus_parsesAllFiveValues() {
        assertEquals(VerificationStatus.PENDING_VERIFICATION, VerificationStatus.fromString("PENDING_VERIFICATION"))
        assertEquals(VerificationStatus.UNDER_REVIEW, VerificationStatus.fromString("UNDER_REVIEW"))
        assertEquals(VerificationStatus.APPROVED, VerificationStatus.fromString("APPROVED"))
        assertEquals(VerificationStatus.REJECTED, VerificationStatus.fromString("REJECTED"))
        assertEquals(VerificationStatus.SUSPENDED, VerificationStatus.fromString("SUSPENDED"))
        assertEquals(VerificationStatus.PENDING_VERIFICATION, VerificationStatus.fromString("bogus"))
    }

    @Test
    fun seller_isApprovedOnlyWhenApproved() {
        assertTrue(Seller(status = "APPROVED").isApproved())
        assertFalse(Seller(status = "UNDER_REVIEW").isApproved())
        assertFalse(Seller().isApproved())
    }

    // ---- Product price helpers (drive every price shown in the app) ----

    @Test
    fun product_effectivePricePrefersDiscount() {
        assertEquals(80.0, Product(price = 100.0, discountPrice = 80.0).effectivePrice(), 0.0)
        assertEquals(100.0, Product(price = 100.0).effectivePrice(), 0.0)
    }

    @Test
    fun product_discountOnlyCountsWhenLowerThanPrice() {
        assertTrue(Product(price = 100.0, discountPrice = 80.0).hasDiscount())
        assertFalse(Product(price = 100.0).hasDiscount())
        assertFalse(Product(price = 100.0, discountPrice = 100.0).hasDiscount())
        assertFalse(Product(price = 100.0, discountPrice = 120.0).hasDiscount())
    }

    @Test
    fun product_savingsAmount() {
        assertEquals(25.0, Product(price = 100.0, discountPrice = 75.0).savingsAmount(), 0.0)
        assertEquals(0.0, Product(price = 100.0).savingsAmount(), 0.0)
    }

    @Test
    fun product_imagesFallsBackToLegacySingleUrl() {
        assertEquals(
            listOf("a", "b"),
            Product(imageUrl = "legacy", imageUrls = listOf("a", "b")).images()
        )
        assertEquals(listOf("legacy"), Product(imageUrl = "legacy").images())
        assertEquals(emptyList<String>(), Product().images())
        assertEquals(emptyList<String>(), Product(imageUrl = "  ").images())
    }

    @Test
    fun product_cutoutPrefersProcessedImage() {
        assertEquals(
            "cutout",
            Product(imageUrl = "orig", cutoutImageUrl = "cutout").cutoutImage()
        )
        assertEquals("orig", Product(imageUrl = "orig").cutoutImage())
        assertEquals(null, Product().cutoutImage())
    }

    // ---- Order helpers ----

    @Test
    fun order_orderStatusAndFix() {
        assertEquals(OrderStatus.CONFIRMED, Order(status = "confirmed").orderStatus())
        assertTrue(Order(deliveryLatitude = 30.9, deliveryLongitude = 75.8).hasDeliveryFix())
        assertFalse(Order().hasDeliveryFix())
        assertFalse(Order(deliveryLatitude = 30.9).hasDeliveryFix())
    }

    @Test
    fun sellerStore_hasFixNeedsBothCoordinates() {
        assertTrue(SellerStore(storeLatitude = 1.0, storeLongitude = 2.0).hasFix())
        assertFalse(SellerStore(storeLatitude = 1.0).hasFix())
        assertFalse(SellerStore().hasFix())
    }

    // ---- KYC constants (must match the DB check constraints) ----

    @Test
    fun docTypeLists_matchExpectedValues() {
        assertEquals(
            listOf("PAN", "GST_CERTIFICATE", "FSSAI_LICENSE", "BANK_PROOF", "ADDRESS_PROOF"),
            SELLER_DOC_TYPES
        )
        assertEquals(
            listOf("DRIVING_LICENCE", "AADHAAR", "PAN", "VEHICLE_RC", "VEHICLE_INSURANCE", "BANK_PROOF"),
            DELIVERY_DOC_TYPES
        )
        assertEquals(listOf("BIKE", "SCOOTER", "BICYCLE", "EV_SCOOTER"), VEHICLE_TYPES)
    }

    @Test
    fun docTypeLabel_coversEveryKnownType() {
        (SELLER_DOC_TYPES + DELIVERY_DOC_TYPES).forEach { type ->
            val label = docTypeLabel(type)
            assertTrue(label.isNotBlank())
            assertFalse(label.contains("_"))
        }
        assertEquals("SOME_NEW_DOC", docTypeLabel("SOME_NEW_DOC"))
    }
}
