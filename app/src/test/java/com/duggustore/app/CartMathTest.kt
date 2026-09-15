package com.duggustore.app

import com.duggustore.app.data.model.CartItem
import com.duggustore.app.data.model.Product
import com.duggustore.app.ui.viewmodel.CartState
import com.duggustore.app.ui.viewmodel.couponDiscountFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cart totals, delivery fee slabs and coupon maths — the money paths. */
class CartMathTest {

    private fun item(price: Double, discountPrice: Double? = null, quantity: Int = 1) =
        CartItem(
            productId = "p",
            quantity = quantity,
            product = Product(price = price, discountPrice = discountPrice)
        )

    // ---- subtotal ----

    @Test
    fun subtotal_usesEffectivePriceTimesQuantity() {
        val state = CartState(
            cartItems = listOf(
                item(price = 100.0, discountPrice = 80.0, quantity = 2), // 160
                item(price = 50.0, quantity = 3) // 150
            )
        )
        assertEquals(310.0, state.subtotal, 0.001)
    }

    @Test
    fun subtotal_emptyCartIsZero() {
        assertEquals(0.0, CartState().subtotal, 0.0)
    }

    // ---- delivery fee slabs (₹29 flat, free at/above ₹299, none on empty) ----

    @Test
    fun deliveryFee_emptyCartIsFree() {
        assertEquals(0.0, CartState().deliveryFee, 0.0)
    }

    @Test
    fun deliveryFee_belowThresholdIsFlat() {
        assertEquals(29.0, CartState(cartItems = listOf(item(100.0))).deliveryFee, 0.0)
        assertEquals(29.0, CartState(cartItems = listOf(item(298.99))).deliveryFee, 0.0)
    }

    @Test
    fun deliveryFee_atOrAboveThresholdIsFree() {
        assertEquals(0.0, CartState(cartItems = listOf(item(299.0))).deliveryFee, 0.0)
        assertEquals(0.0, CartState(cartItems = listOf(item(1000.0))).deliveryFee, 0.0)
    }

    // ---- minimum order (₹99) ----

    @Test
    fun minimumOrder_flagsOnlyNonEmptyCartsBelow99() {
        assertFalse(CartState().isBelowMinimumOrder) // empty cart is not "below minimum"
        assertTrue(CartState(cartItems = listOf(item(50.0))).isBelowMinimumOrder)
        assertTrue(CartState(cartItems = listOf(item(98.99))).isBelowMinimumOrder)
        assertFalse(CartState(cartItems = listOf(item(99.0))).isBelowMinimumOrder)
    }

    // ---- total / savings / count ----

    @Test
    fun total_isSubtotalPlusFeeMinusCoupon() {
        val state = CartState(
            cartItems = listOf(item(100.0, quantity = 2)), // subtotal 200 + fee 29
            couponDiscount = 20.0
        )
        assertEquals(209.0, state.total, 0.001)
    }

    @Test
    fun savings_countsOnlyDiscountedLines() {
        val state = CartState(
            cartItems = listOf(
                item(price = 100.0, discountPrice = 80.0, quantity = 2), // saves 40
                item(price = 50.0) // saves 0
            )
        )
        assertEquals(40.0, state.savings, 0.001)
    }

    @Test
    fun itemCount_sumsQuantities() {
        val state = CartState(cartItems = listOf(item(10.0, quantity = 2), item(10.0, quantity = 3)))
        assertEquals(5, state.itemCount)
    }

    // ---- couponDiscountFor ----

    @Test
    fun coupon_percentAppliedWithoutCap() {
        assertEquals(50.0, couponDiscountFor(500.0, 10, 1000), 0.001)
    }

    @Test
    fun coupon_cappedAtMaxDiscount() {
        assertEquals(100.0, couponDiscountFor(2000.0, 10, 100), 0.001)
    }

    @Test
    fun coupon_zeroPercentOrZeroMaxGivesNothing() {
        assertEquals(0.0, couponDiscountFor(500.0, 0, 100), 0.0)
        assertEquals(0.0, couponDiscountFor(500.0, 10, 0), 0.0)
    }
}
