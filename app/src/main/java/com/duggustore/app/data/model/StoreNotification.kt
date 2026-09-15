package com.duggustore.app.data.model

/**
 * One row of the notifications table, in the shape the UI reads.
 *
 * These are written server-side: order triggers insert a row, and a trigger on
 * that insert sends the push. So a notification exists once, on the server —
 * the app displays it and marks it read, it does not invent one.
 */
data class StoreNotification(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: String,
    val orderId: String,
    val kind: Kind
) {
    enum class Kind { Placed, Confirmed, Preparing, ReadyForPickup, OutForDelivery, Delivered, Cancelled }
}
