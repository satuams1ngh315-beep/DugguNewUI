package com.duggustore.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.duggustore.app.MainActivity
import com.duggustore.app.R
import com.duggustore.app.data.remote.SessionManager
import com.duggustore.app.data.repository.DeviceTokenRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val CHANNEL_ID = "duggustore_default"

/**
 * The one thing the in-app, DB-backed notification list can't do — surface
 * something while the app isn't open. Delivered via FCM; the row a
 * customer/seller sees in-app when they do open the app is unrelated and
 * unaffected by whether this fired.
 */
object PushNotifications {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Order & store updates",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    /** Wraps FCM's Task-based token fetch as a suspend call, same pattern as BackgroundRemover's ML Kit calls. */
    suspend fun currentFcmToken(): String? = try {
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Called once a user is known to be signed in — onNewToken alone only
     * fires when the token is first generated or rotates, not on every
     * app start, so a token already issued before this login would
     * otherwise never get associated with this user.
     */
    fun registerCurrentToken(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            currentFcmToken()?.let { DeviceTokenRepository().registerToken(userId, it) }
        }
    }
}

class DugguFcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val userId = SessionManager.getUserId() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            DeviceTokenRepository().registerToken(userId, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // No signed-in user on this device: the message belongs to an account
        // that has since signed out (token deletion on sign-out is
        // best-effort, and a push can already be in flight), so don't surface
        // it. Notifications belong to the session, not the hardware.
        if (SessionManager.getUserId() == null) return

        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val orderId = message.data["order_id"]

        PushNotifications.ensureChannel(this)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_notifications", true)
            orderId?.let { putExtra("order_id", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (orderId ?: title).hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationId = (orderId ?: title).hashCode()
        getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }
}
