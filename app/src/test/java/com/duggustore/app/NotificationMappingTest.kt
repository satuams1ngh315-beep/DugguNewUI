package com.duggustore.app

import com.duggustore.app.data.model.StoreNotification
import com.duggustore.app.data.repository.DbNotification
import com.duggustore.app.data.repository.NotificationsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DB notification rows map to timeline icons by title keyword — these titles
 * must stay in sync with the notify_order_status() trigger in
 * supabase_production_addon.sql.
 */
class NotificationMappingTest {

    private val repo = NotificationsRepository()

    private fun map(title: String, orderId: String? = "order-1"): StoreNotification =
        with(repo) {
            DbNotification(
                id = "n1",
                userId = "u1",
                title = title,
                message = "hello",
                createdAt = "2026-01-01T00:00:00Z",
                orderId = orderId
            ).toStoreNotification()
        }

    @Test
    fun everyOrderStageMapsToItsKind() {
        assertEquals(StoreNotification.Kind.Confirmed, map("Order confirmed").kind)
        assertEquals(StoreNotification.Kind.Preparing, map("Preparing your order").kind)
        assertEquals(StoreNotification.Kind.ReadyForPickup, map("Ready for pickup").kind)
        assertEquals(StoreNotification.Kind.OutForDelivery, map("Out for delivery").kind)
        assertEquals(StoreNotification.Kind.Delivered, map("Delivered").kind)
        assertEquals(StoreNotification.Kind.Cancelled, map("Order cancelled").kind)
    }

    @Test
    fun deliveredDoesNotConfuseWithOutForDelivery() {
        // "delivery" is checked before "delivered" in the mapper, so the
        // Delivered title must not contain the word "delivery".
        assertEquals(StoreNotification.Kind.Delivered, map("Delivered").kind)
        assertEquals(StoreNotification.Kind.Delivered, map("Order delivered").kind)
    }

    @Test
    fun unknownTitlesFallBackToPlaced() {
        assertEquals(StoreNotification.Kind.Placed, map("Order placed").kind)
        assertEquals(StoreNotification.Kind.Placed, map("Something new").kind)
    }

    @Test
    fun fieldsPassThroughAndMissingOrderIdIsEmpty() {
        val n = map("Order confirmed")
        assertEquals("n1", n.id)
        assertEquals("Order confirmed", n.title)
        assertEquals("hello", n.body)
        assertEquals("2026-01-01T00:00:00Z", n.timestamp)
        assertEquals("order-1", n.orderId)

        assertEquals("", map("Order confirmed", orderId = null).orderId)
    }
}
